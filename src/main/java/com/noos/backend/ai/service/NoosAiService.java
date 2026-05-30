package com.noos.backend.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.ai.dto.AiFeedbackParseRequest;
import com.noos.backend.ai.dto.DashboardSummaryRequest;
import com.noos.backend.ai.dto.DeviceTroubleshootRequest;
import com.noos.backend.ai.dto.EegRecognitionRequest;
import com.noos.backend.ai.dto.InterventionGenerationRequest;
import com.noos.backend.ai.dto.PlanetRecommendationRequest;
import com.noos.backend.ai.dto.SessionCoachRequest;
import com.noos.backend.ai.dto.StateExplanationRequest;
import com.noos.backend.lighting.service.WizLightingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class NoosAiService {

    private static final String ACE_STEP_FALLBACK_WARNING =
            "ACE-Step generation is unavailable. Falling back to the NOOS intervention plan without generated audio.";
    private static final String DEFAULT_ACE_STEP_MODEL_NAME = "acestep-v15-turbo";
    private static final Duration ACE_STEP_HEALTHCHECK_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ACE_STEP_STARTUP_WAIT = Duration.ofSeconds(25);
    private static final int DEFAULT_INTERVENTION_DURATION_SEC = 90;
    private static final int MIN_INTERVENTION_DURATION_SEC = 10;
    private static final int MAX_INTERVENTION_DURATION_SEC = 600;
    private static final int PROCESS_LOG_MAX_BYTES = 12_000;
    private static final long REMOTE_AUDIO_DOWNLOAD_MAX_BYTES = 250L * 1024L * 1024L;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final List<String> CANONICAL_AXES = List.of(
            "focus_readiness",
            "stress_load",
            "fatigue_risk",
            "relaxation_level",
            "cortical_arousal",
            "mental_workload"
    );

    private final ObjectMapper objectMapper;
    private final String pythonBin;
    private final String aceStepBaseUrl;
    private final long aceStepTimeoutSec;
    private final boolean aceStepAutoStart;
    private final String aceStepModelName;
    private final boolean aceStepUseEnhancedRequest;
    private final String aceStepLmModel;
    private final int aceStepRequestDurationCapSec;
    private final int aceStepInferenceSteps;
    private final boolean gemmaEnabled;
    private final String gemmaBaseUrl;
    private final long gemmaTimeoutSec;
    private final WizLightingService wizLightingService;
    private final HttpClient httpClient;
    private final Object aceStepServerLock = new Object();
    private volatile Process aceStepServerProcess;

    @Value("${noos.ai.root:}")
    private String configuredAiRoot;

    public NoosAiService(
            ObjectMapper objectMapper,
            @Value("${noos.ai.python-bin:python}") String pythonBin,
            @Value("${noos.ai.ace-step.base-url:http://127.0.0.1:8011}") String aceStepBaseUrl,
            @Value("${noos.ai.ace-step.timeout-sec:900}") long aceStepTimeoutSec,
            @Value("${noos.ai.ace-step.auto-start:true}") boolean aceStepAutoStart,
            @Value("${noos.ai.ace-step.model:acestep-v15-turbo}") String aceStepModelName,
            @Value("${noos.ai.ace-step.use-enhanced-request:false}") boolean aceStepUseEnhancedRequest,
            @Value("${noos.ai.ace-step.lm-model:}") String aceStepLmModel,
            @Value("${noos.ai.ace-step.request-duration-cap-sec:0}") int aceStepRequestDurationCapSec,
            @Value("${noos.ai.ace-step.inference-steps:0}") int aceStepInferenceSteps,
            @Value("${noos.ai.gemma.enabled:true}") boolean gemmaEnabled,
            @Value("${noos.ai.gemma.base-url:http://127.0.0.1:8091}") String gemmaBaseUrl,
            @Value("${noos.ai.gemma.timeout-sec:120}") long gemmaTimeoutSec,
            WizLightingService wizLightingService
    ) {
        this.objectMapper = objectMapper;
        this.pythonBin = pythonBin;
        this.aceStepBaseUrl = aceStepBaseUrl;
        this.aceStepTimeoutSec = aceStepTimeoutSec;
        this.aceStepAutoStart = aceStepAutoStart;
        this.aceStepModelName = aceStepModelName;
        this.aceStepUseEnhancedRequest = aceStepUseEnhancedRequest;
        this.aceStepLmModel = aceStepLmModel;
        this.aceStepRequestDurationCapSec = aceStepRequestDurationCapSec;
        this.aceStepInferenceSteps = aceStepInferenceSteps;
        this.gemmaEnabled = gemmaEnabled;
        this.gemmaBaseUrl = gemmaBaseUrl;
        this.gemmaTimeoutSec = gemmaTimeoutSec;
        this.wizLightingService = wizLightingService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, gemmaTimeoutSec)))
                .build();
    }

    public Map<String, Object> recognize(EegRecognitionRequest request, List<Map<String, Object>> rawReadings) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_type", "recognition");
        payload.put("device_type", request.deviceType() != null && !request.deviceType().isBlank() ? request.deviceType() : "Muse S Athena");
        payload.put("measured_at", request.measuredAt());
        payload.put("sample_rate_hz", request.sampleRateHz() != null ? request.sampleRateHz() : 256);
        payload.put("context", Map.of(
                "measurement_duration_sec", request.measurementDurationSec() != null ? request.measurementDurationSec() : 0,
                "source", rawReadings != null && !rawReadings.isEmpty() ? "frontend-raw-chunk-upload" : "frontend-band-summary"
        ));

        if (rawReadings != null && !rawReadings.isEmpty()) {
            payload.put("readings", rawReadings);
        }

        Map<String, Object> bandSummary = new LinkedHashMap<>();
        bandSummary.put("sampleCount", request.sampleCount() != null ? request.sampleCount() : 0);
        bandSummary.put("dominantBand", request.dominantBand());
        bandSummary.put("delta", safeNumber(request.delta(), 0.0));
        bandSummary.put("theta", safeNumber(request.theta(), 0.0));
        bandSummary.put("alpha", safeNumber(request.alpha(), 0.0));
        bandSummary.put("beta", safeNumber(request.beta(), 0.0));
        bandSummary.put("gamma", safeNumber(request.gamma(), 0.0));
        payload.put("band_summary", bandSummary);

        Map<String, Object> recognitionResult = runCli(payload, false);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("recognitionResult", recognitionResult);
        response.put("currentState", extractCurrentState(recognitionResult));
        response.put("stateLabel", nestedString(recognitionResult, "state_profile", "label"));
        return response;
    }

    public Map<String, Object> recognizeFromSummary(EegRecognitionRequest request) {
        return recognize(request, List.of());
    }

    public Map<String, Object> parseFeedback(AiFeedbackParseRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("feedbackText", request.feedbackText());
        payload.put("rating", request.rating());
        payload.put("planet", request.planet());
        payload.put("targetState", request.targetState());
        payload.put("measuredState", request.measuredState());
        payload.put("measuredSource", request.measuredSource());
        payload.put("currentState", request.currentState() != null ? request.currentState() : Map.of());
        return invokeGemmaTask("feedback-parse", payload, true);
    }

    public Map<String, Object> recommendPlanet(PlanetRecommendationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intentText", request.intentText());
        payload.put("desiredOutcome", request.desiredOutcome());
        payload.put("memoText", request.memoText());
        payload.put("currentState", request.currentState() != null ? request.currentState() : Map.of());
        payload.put("feedbackHistory", request.feedbackHistory() != null ? request.feedbackHistory() : List.of());
        payload.put("requestedDurationSec", request.requestedDurationSec());
        return invokeGemmaTask("planet-recommendation", payload, true);
    }

    public Map<String, Object> explainState(StateExplanationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", request.title());
        payload.put("stateLabel", request.stateLabel());
        payload.put("summary", request.summary());
        payload.put("currentState", request.currentState() != null ? request.currentState() : Map.of());
        payload.put("targetPlanet", request.targetPlanet());
        return invokeGemmaTask("state-explanation", payload, true);
    }

    public Map<String, Object> summarizeDashboard(DashboardSummaryRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("feedbackHistory", request.feedbackHistory() != null ? request.feedbackHistory() : List.of());
        payload.put("memoText", request.memoText());
        payload.put("currentState", request.currentState() != null ? request.currentState() : Map.of());
        return invokeGemmaTask("dashboard-summary", payload, true);
    }

    public Map<String, Object> coachSession(SessionCoachRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planet", request.planet());
        payload.put("intentText", request.intentText());
        payload.put("recommendation", request.recommendation() != null ? request.recommendation() : Map.of());
        payload.put("recommendedDurationSec", request.recommendedDurationSec());
        return invokeGemmaTask("session-coach", payload, true);
    }

    public Map<String, Object> troubleshootDevice(DeviceTroubleshootRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("issueText", request.issueText());
        payload.put("stage", request.stage());
        payload.put("browser", request.browser());
        payload.put("deviceType", request.deviceType());
        return invokeGemmaTask("device-troubleshoot", payload, true);
    }

    public Map<String, Object> generateIntervention(InterventionGenerationRequest request) {
        if (request.planet() == null || request.planet().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "planet is required");
        }

        int durationSec = normalizeInterventionDuration(request.durationSec());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_type", "intervention");
        payload.put("planet", request.planet());
        payload.put("duration_sec", durationSec);
        payload.put("candidate_count_override", request.candidateCountOverride() != null ? request.candidateCountOverride() : 1);

        if (request.currentState() != null && !request.currentState().isEmpty()) {
            payload.put("current_state", request.currentState());
        }
        if (request.recognitionResult() != null && !request.recognitionResult().isEmpty()) {
            payload.put("recognition_result", request.recognitionResult());
        }
        if (request.feedbackHistory() != null && !request.feedbackHistory().isEmpty()) {
            payload.put("feedback_profile", buildFeedbackProfile(request.feedbackHistory()));
        }
        if (request.intentContext() != null && !request.intentContext().isEmpty()) {
            payload.put("intent_context", request.intentContext());
        }

        Map<String, Object> interventionResult = null;
        String generationWarning = null;
        boolean aceStepAvailable = false;

        if (ensureAceStepServerReady()) {
            try {
                interventionResult = runCli(payload, true);
            } catch (ResponseStatusException error) {
                if (!isRetryableAceStepFailure(error)) {
                    throw error;
                }
                if (restartAceStepServer()) {
                    try {
                        interventionResult = runCli(payload, true);
                    } catch (ResponseStatusException retryError) {
                        if (!isRetryableAceStepFailure(retryError)) {
                            throw retryError;
                        }
                    }
                }
            }
        }

        if (interventionResult == null) {
            interventionResult = runCli(payload, false);
            generationWarning = ACE_STEP_FALLBACK_WARNING;
        }

        if (generationWarning == null && isRemoteAceStepBaseUrl()) {
            try {
                materializeRemoteAceStepAudio(interventionResult);
            } catch (ResponseStatusException error) {
                generationWarning = error.getReason();
            }
        }

        String audioPath = extractAudioFilePath(interventionResult);
        aceStepAvailable = audioPath != null && isStreamableAudioPath(audioPath);
        if (!aceStepAvailable && generationWarning == null) {
            generationWarning = ACE_STEP_FALLBACK_WARNING;
        }

        Map<String, Object> recognitionResult = request.recognitionResult() != null ? request.recognitionResult() : Map.of();
        Map<String, Object> intentContext = request.intentContext() != null ? request.intentContext() : Map.of();
        Map<String, Object> explanationPayload = new LinkedHashMap<>();
        explanationPayload.put("title", nestedString(recognitionResult, "state_profile", "label"));
        explanationPayload.put("stateLabel", nestedString(recognitionResult, "state_profile", "label"));
        explanationPayload.put("summary", "현재 상태와 목표 행성 사이의 전환 계획을 설명합니다.");
        explanationPayload.put("currentState", payload.getOrDefault("current_state", Map.of()));
        explanationPayload.put("targetPlanet", request.planet());
        Map<String, Object> llmStateExplanation = invokeGemmaTaskSafely("state-explanation", explanationPayload);

        Map<String, Object> coachPayload = new LinkedHashMap<>();
        coachPayload.put("planet", request.planet());
        coachPayload.put("intentText", stringValue(intentContext.get("intentText")));
        coachPayload.put("recommendation", mapValue(intentContext.get("recommendation")));
        coachPayload.put("recommendedDurationSec", request.durationSec() != null ? request.durationSec() : 90);
        Map<String, Object> llmSessionCoach = invokeGemmaTaskSafely("session-coach", coachPayload);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("interventionResult", interventionResult);
        response.put("audioUrl", audioPath != null ? buildAudioProxyUrl(audioPath) : null);
        response.put("audioDurationSec", extractGeneratedAudioDuration(interventionResult));
        response.put("trackName", extractTrackName(interventionResult));
        response.put("currentState", payload.get("current_state"));
        response.put("feedbackProfile", payload.getOrDefault("feedback_profile", Map.of()));
        response.put("llmStateExplanation", llmStateExplanation);
        response.put("llmSessionCoach", llmSessionCoach);
        response.put("aceStepAvailable", aceStepAvailable);
        response.put("generationWarning", generationWarning);
        response.put("wizLighting", maybeStartWizLighting(interventionResult));
        return response;
    }

    public Map<String, Object> prewarmIntervention() {
        boolean aceStepReady = ensureAceStepServerReady();
        boolean aceStepModelReady = false;
        String aceStepPrewarmError = null;
        if (aceStepReady) {
            try {
                aceStepModelReady = ensureAceStepModelInitialized();
            } catch (ResponseStatusException error) {
                aceStepPrewarmError = error.getReason();
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("aceStepReady", aceStepReady);
        response.put("aceStepModelReady", aceStepModelReady);
        response.put("aceStepBaseUrl", aceStepBaseUrl);
        if (aceStepPrewarmError != null && !aceStepPrewarmError.isBlank()) {
            response.put("aceStepPrewarmError", aceStepPrewarmError);
        }
        return response;
    }

    public ResponseEntity<Resource> streamGeneratedAudio(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
        }

        Path audioPath = Paths.get(rawPath).toAbsolutePath().normalize();

        boolean allowed = allowedAudioRoots().stream().anyMatch(audioPath::startsWith);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "audio path is outside the allowed NOOS AI directories");
        }
        if (!Files.exists(audioPath) || !Files.isRegularFile(audioPath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "generated audio file not found");
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            String detected = Files.probeContentType(audioPath);
            if (detected != null) {
                mediaType = MediaType.parseMediaType(detected);
            } else if (audioPath.toString().endsWith(".mp3")) {
                mediaType = MediaType.parseMediaType("audio/mpeg");
            }
        } catch (IOException ignored) {
            if (audioPath.toString().endsWith(".mp3")) {
                mediaType = MediaType.parseMediaType("audio/mpeg");
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new FileSystemResource(audioPath));
    }

    private Map<String, Object> invokeGemmaTask(String taskName, Map<String, Object> payload, boolean failOnUnavailable) {
        if (!gemmaEnabled) {
            if (failOnUnavailable) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemma integration is disabled");
            }
            return Map.of("task", taskName, "output", Map.of(), "response_source", "disabled");
        }

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of("payload", payload));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gemmaBaseUrl.replaceAll("/$", "") + "/tasks/" + taskName))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(Math.max(10, gemmaTimeoutSec)))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (failOnUnavailable) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "Gemma task failed: " + response.statusCode() + " " + response.body()
                    );
                }
                return Map.of("task", taskName, "output", Map.of(), "response_source", "error");
            }

            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (failOnUnavailable) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to reach Gemma task service", error);
            }
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("task", taskName);
            fallback.put("output", Map.of());
            fallback.put("response_source", "error");
            fallback.put("error_detail", error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
            return fallback;
        }
    }

    private Map<String, Object> invokeGemmaTaskSafely(String taskName, Map<String, Object> payload) {
        return invokeGemmaTask(taskName, payload, false);
    }

    private Map<String, Object> buildFeedbackProfile(List<Map<String, Object>> feedbackHistory) {
        if (feedbackHistory == null || feedbackHistory.isEmpty()) {
            return Map.of();
        }

        double tempoDelta = 0.0;
        double energyDelta = 0.0;
        double brightnessDelta = 0.0;
        double densityDelta = 0.0;
        double tensionDelta = 0.0;
        double textureDelta = 0.0;
        double lightingBrightnessDelta = 0.0;
        double cctDelta = 0.0;
        double luxDelta = 0.0;
        double motionDelta = 0.0;
        double durationDelta = 0.0;
        int counted = 0;
        Map<String, Integer> preferredPlanets = new HashMap<>();
        Map<String, Integer> avoidPlanets = new HashMap<>();
        List<String> summaryFragments = new ArrayList<>();

        for (Map<String, Object> entry : feedbackHistory) {
            Map<String, Object> llm = mapValue(entry.get("llm"));
            Map<String, Object> structured = mapValue(llm.get("structuredFeedback"));
            if (structured.isEmpty()) {
                structured = mapValue(llm.get("structured_feedback"));
            }
            Map<String, Object> recommended = mapValue(structured.get("recommendedAdjustments"));
            if (recommended.isEmpty()) {
                recommended = mapValue(structured.get("recommended_adjustments"));
            }
            Map<String, Object> music = mapValue(recommended.get("music_adjustments"));
            Map<String, Object> lighting = mapValue(recommended.get("lighting_adjustments"));
            Map<String, Object> session = mapValue(recommended.get("session_adjustments"));

            if (!music.isEmpty() || !lighting.isEmpty() || !session.isEmpty()) {
                counted += 1;
            }

            tempoDelta += numberValue(music.get("tempo_delta"), 0.0);
            energyDelta += numberValue(music.get("energy_delta"), 0.0);
            brightnessDelta += numberValue(music.get("brightness_delta"), 0.0);
            densityDelta += numberValue(music.get("density_delta"), 0.0);
            tensionDelta += numberValue(music.get("tension_delta"), 0.0);
            textureDelta += numberValue(music.get("texture_delta"), 0.0);

            lightingBrightnessDelta += numberValue(lighting.get("brightness_delta"), 0.0);
            cctDelta += numberValue(lighting.get("cct_delta"), 0.0);
            luxDelta += numberValue(lighting.get("lux_delta"), 0.0);
            motionDelta += numberValue(lighting.get("motion_delta"), 0.0);

            durationDelta += numberValue(session.get("duration_delta_sec"), 0.0);
            accumulatePlanetVotes(preferredPlanets, stringList(session.get("preferred_planets")));
            accumulatePlanetVotes(avoidPlanets, stringList(session.get("avoid_planets")));

            String summary = stringValue(llm.get("summary"));
            if (summary != null && !summary.isBlank()) {
                summaryFragments.add(summary);
            }
        }

        if (counted == 0) {
            return Map.of();
        }

        return Map.of(
                "music_adjustments", Map.of(
                        "tempo_delta", roundedInt(tempoDelta / counted),
                        "energy_delta", rounded(numberValue(energyDelta / counted, 0.0)),
                        "brightness_delta", rounded(numberValue(brightnessDelta / counted, 0.0)),
                        "density_delta", rounded(numberValue(densityDelta / counted, 0.0)),
                        "tension_delta", rounded(numberValue(tensionDelta / counted, 0.0)),
                        "texture_delta", rounded(numberValue(textureDelta / counted, 0.0))
                ),
                "lighting_adjustments", Map.of(
                        "brightness_delta", roundedInt(lightingBrightnessDelta / counted),
                        "cct_delta", roundedInt(cctDelta / counted),
                        "lux_delta", roundedInt(luxDelta / counted),
                        "motion_delta", rounded(numberValue(motionDelta / counted, 0.0))
                ),
                "session_adjustments", Map.of(
                        "duration_delta_sec", roundedInt(durationDelta / counted),
                        "preferred_planets", topPlanetVotes(preferredPlanets),
                        "avoid_planets", topPlanetVotes(avoidPlanets)
                ),
                "summary", summaryFragments.stream().filter(Objects::nonNull).limit(3).collect(Collectors.toList())
        );
    }

    private Map<String, Object> runCli(Map<String, Object> payload, boolean generateAceStep) {
        Path aiRoot = resolveAiRoot();
        Path inputJson = null;
        Path outputJson = null;
        Path logFile = null;

        try {
            inputJson = Files.createTempFile("noos-ai-input-", ".json");
            outputJson = Files.createTempFile("noos-ai-output-", ".json");
            logFile = Files.createTempFile("noos-ai-process-", ".log");
            objectMapper.writeValue(inputJson.toFile(), payload);

            String pythonExecutable = resolvePythonExecutable(aiRoot);
            List<String> command = new ArrayList<>();
            command.add(pythonExecutable);
            command.add("-m");
            command.add("noos_ai.cli");
            command.add(inputJson.toString());
            command.add("--output-json");
            command.add(outputJson.toString());

            if (generateAceStep) {
                command.add("--generate-ace-step");
                if (aceStepUseEnhancedRequest) {
                    command.add("--use-enhanced-request");
                }
                command.add("--api-base-url");
                command.add(aceStepBaseUrl);
                command.add("--timeout-sec");
                command.add(String.valueOf(aceStepTimeoutSec));
                command.add("--poll-interval-sec");
                command.add("2.0");
            }

            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(aiRoot.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile());
            Map<String, String> env = processBuilder.environment();
            if (generateAceStep) {
                configureAceStepGenerationEnvironment(env);
            }
            Process process = processBuilder.start();

            boolean completed = process.waitFor(generateAceStep ? aceStepTimeoutSec + 60 : 60, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "NOOS AI process timed out" + formatProcessLogSuffix(readProcessLog(logFile))
                );
            }
            if (process.exitValue() != 0) {
                String logs = readProcessLog(logFile);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NOOS AI process failed: " + logs);
            }

            return objectMapper.readValue(outputJson.toFile(), MAP_TYPE);
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to execute NOOS AI process", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "NOOS AI process was interrupted", error);
        } finally {
            deleteIfExists(inputJson);
            deleteIfExists(outputJson);
            deleteIfExists(logFile);
        }
    }

    private Path resolveAiRoot() {
        return resolveRepoRoot().resolve("ai").toAbsolutePath().normalize();
    }

    private String resolvePythonExecutable(Path aiRoot) {
        if (hasText(pythonBin) && !isGenericPythonCommand(pythonBin)) {
            return pythonBin.trim();
        }

        Path preferredVenvPython = isWindows()
                ? aiRoot.resolve(".venv").resolve("Scripts").resolve("python.exe")
                : aiRoot.resolve(".venv").resolve("bin").resolve("python");

        if (Files.isRegularFile(preferredVenvPython)) {
            return preferredVenvPython.toAbsolutePath().normalize().toString();
        }

        return hasText(pythonBin) ? pythonBin.trim() : (isWindows() ? "python" : "python3");
    }

    private Path resolveRepoRoot() {
        if (hasText(configuredAiRoot)) {
            return Paths.get(configuredAiRoot).toAbsolutePath().normalize();
        }
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        if (Files.isDirectory(cwd.resolve("ai")) && Files.isDirectory(cwd.resolve("frontend"))) {
            return cwd;
        }
        if (cwd.getFileName() != null && "backend".equalsIgnoreCase(cwd.getFileName().toString()) && cwd.getParent() != null) {
            return cwd.getParent();
        }
        if (cwd.getParent() != null && Files.isDirectory(cwd.getParent().resolve("ai"))) {
            return cwd.getParent();
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not resolve NOOS repository root");
    }

    private void deleteIfExists(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private int normalizeInterventionDuration(Integer requestedDurationSec) {
        int durationSec = requestedDurationSec != null ? requestedDurationSec : DEFAULT_INTERVENTION_DURATION_SEC;
        if (durationSec < MIN_INTERVENTION_DURATION_SEC || durationSec > MAX_INTERVENTION_DURATION_SEC) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "durationSec must be between " + MIN_INTERVENTION_DURATION_SEC + " and " + MAX_INTERVENTION_DURATION_SEC + " seconds"
            );
        }
        return durationSec;
    }

    private Map<String, Object> maybeStartWizLighting(Map<String, Object> interventionResult) {
        if (wizLightingService == null || !wizLightingService.shouldAutoApply()) {
            return Map.of("enabled", wizLightingService != null && wizLightingService.shouldAutoApply());
        }
        try {
            return wizLightingService.startAlternatingFromPayload(Map.of("interventionResult", interventionResult));
        } catch (ResponseStatusException error) {
            return Map.of(
                    "enabled", true,
                    "started", false,
                    "error", error.getReason() != null ? error.getReason() : error.getMessage()
            );
        }
    }

    private void configureAceStepGenerationEnvironment(Map<String, String> env) {
        env.put("NOOS_ACE_STEP_MODEL", normalizedAceStepModelName());
        putIntEnv(env, "NOOS_ACE_STEP_REQUEST_DURATION_CAP_SEC", aceStepRequestDurationCapSec, defaultAceStepRequestDurationCapSec());
        putIntEnv(env, "NOOS_ACE_STEP_INFERENCE_STEPS", aceStepInferenceSteps, defaultAceStepInferenceSteps());
        env.put("NOOS_ACE_STEP_ENABLE_LM", aceStepUseEnhancedRequest ? "true" : "false");
        String lmModel = normalizedAceStepLmModel();
        if (!lmModel.isBlank()) {
            env.put("NOOS_ACE_STEP_LM_MODEL", lmModel);
        }
    }

    private void putIntEnv(Map<String, String> env, String key, int configuredValue, int defaultValue) {
        if (configuredValue > 0) {
            env.put(key, String.valueOf(configuredValue));
        } else {
            env.putIfAbsent(key, String.valueOf(defaultValue));
        }
    }

    private int defaultAceStepRequestDurationCapSec() {
        return isAppleSiliconMac() && !isRemoteAceStepBaseUrl() ? 30 : 120;
    }

    private int defaultAceStepInferenceSteps() {
        return isAppleSiliconMac() && !isRemoteAceStepBaseUrl() ? 4 : 8;
    }

    private String normalizedAceStepModelName() {
        String model = aceStepModelName != null ? aceStepModelName.trim() : "";
        return model.isBlank() ? DEFAULT_ACE_STEP_MODEL_NAME : model;
    }

    private String normalizedAceStepLmModel() {
        String configured = aceStepLmModel != null ? aceStepLmModel.trim() : "";
        if (!configured.isBlank()) {
            return configured;
        }
        return isRemoteAceStepBaseUrl() ? "acestep-5Hz-lm-1.7B" : "acestep-5Hz-lm-0.6B";
    }

    private String readProcessLog(Path logFile) {
        if (logFile == null || !Files.exists(logFile)) {
            return "";
        }
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            long size = file.length();
            long start = Math.max(0, size - PROCESS_LOG_MAX_BYTES);
            file.seek(start);
            byte[] bytes = new byte[(int) (size - start)];
            file.readFully(bytes);
            String logs = new String(bytes, StandardCharsets.UTF_8).trim();
            return start > 0 ? "[truncated]\n" + logs : logs;
        } catch (IOException ignored) {
            return "";
        }
    }

    private String formatProcessLogSuffix(String logs) {
        return logs == null || logs.isBlank() ? "" : ": " + logs;
    }

    private double safeNumber(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isGenericPythonCommand(String value) {
        if (!hasText(value)) {
            return true;
        }
        String normalized = value.trim().toLowerCase();
        return "python".equals(normalized) || "python3".equals(normalized);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private Map<String, Object> extractCurrentState(Map<String, Object> recognitionResult) {
        Map<String, Object> currentState = new LinkedHashMap<>();
        Map<String, Object> stateProfile = mapValue(recognitionResult.get("state_profile"));
        Map<String, Object> dimensions = mapValue(stateProfile.get("dimensions"));

        for (String axis : CANONICAL_AXES) {
            Map<String, Object> axisPayload = mapValue(dimensions.get(axis));
            Object score = axisPayload.get("score");
            currentState.put(axis, score instanceof Number ? ((Number) score).doubleValue() : 0.5);
        }
        return currentState;
    }

    private String nestedString(Map<String, Object> root, String outerKey, String innerKey) {
        return stringValue(mapValue(root.get(outerKey)).get(innerKey));
    }

    private String extractTrackName(Map<String, Object> interventionResult) {
        Map<String, Object> planetProfile = mapValue(interventionResult.get("planet_profile"));
        String title = stringValue(planetProfile.get("title"));
        if (title == null || title.isBlank()) {
            title = "NOOS";
        }
        return title + " AI Session";
    }

    private Integer extractGeneratedAudioDuration(Map<String, Object> interventionResult) {
        Map<String, Object> aceStepJob = mapValue(interventionResult.get("ace_step_job"));
        List<Map<String, Object>> parsedEntries = listOfMaps(aceStepJob.get("parsed_entries"));
        if (!parsedEntries.isEmpty()) {
            Map<String, Object> firstEntry = parsedEntries.get(0);
            Map<String, Object> meta = mapValue(firstEntry.get("meta"));
            if (meta.isEmpty()) {
                meta = mapValue(firstEntry.get("metas"));
            }
            Object duration = firstNonNull(
                    meta.get("duration"),
                    meta.get("audio_duration"),
                    firstEntry.get("duration")
            );
            if (duration instanceof Number number) {
                return (int) Math.round(number.doubleValue());
            }
            if (duration instanceof String string) {
                try {
                    return (int) Math.round(Double.parseDouble(string));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        Map<String, Object> musicSpec = mapValue(interventionResult.get("music_spec"));
        Object durationSec = musicSpec.get("duration_sec");
        if (durationSec instanceof Number number) {
            return (int) Math.round(number.doubleValue());
        }
        return null;
    }

    private void materializeRemoteAceStepAudio(Map<String, Object> interventionResult) {
        Map<String, Object> aceStepJob = mapValue(interventionResult.get("ace_step_job"));
        List<Map<String, Object>> parsedEntries = listOfMaps(aceStepJob.get("parsed_entries"));
        if (parsedEntries.isEmpty()) {
            return;
        }

        Map<String, Object> taskResult = mapValue(aceStepJob.get("task_result"));
        String taskId = stringValue(taskResult.get("task_id"));
        for (int index = 0; index < parsedEntries.size(); index += 1) {
            Map<String, Object> entry = parsedEntries.get(index);
            String remoteFile = stringValue(entry.get("file"));
            if (remoteFile == null || remoteFile.isBlank()) {
                continue;
            }
            Path localAudio = downloadRemoteAceStepAudio(remoteFile, taskId, index);
            entry.put("remote_file", remoteFile);
            entry.put("file", buildAceStepAudioFileReference(localAudio));
            entry.put("local_file_path", localAudio.toString());
        }
    }

    private Path downloadRemoteAceStepAudio(String fileReference, String taskId, int index) {
        URI audioUri = resolveAceStepAudioUri(fileReference);
        String extension = inferAudioExtension(fileReference);
        String safeTaskId = sanitizeFileName(taskId != null && !taskId.isBlank() ? taskId : "ace-step-audio");
        Path outputDir = resolveAiRoot().resolve("generated").resolve("ace_step_audio").toAbsolutePath().normalize();
        Path outputPath = outputDir.resolve(safeTaskId + "-" + index + extension).normalize();
        if (!outputPath.startsWith(outputDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ACE-Step audio download path was invalid");
        }

        try {
            Files.createDirectories(outputDir);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(audioUri)
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(Math.max(120, aceStepTimeoutSec)))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "ACE-Step remote audio download failed with HTTP " + response.statusCode()
                );
            }
            try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(outputPath)) {
                copyWithLimit(input, output, REMOTE_AUDIO_DOWNLOAD_MAX_BYTES);
            }
            return outputPath;
        } catch (IOException error) {
            deleteIfExists(outputPath);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to download ACE-Step remote audio", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            deleteIfExists(outputPath);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ACE-Step remote audio download was interrupted", error);
        } catch (ResponseStatusException error) {
            deleteIfExists(outputPath);
            throw error;
        }
    }

    private void copyWithLimit(InputStream input, OutputStream output, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long copied = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            copied += read;
            if (copied > maxBytes) {
                throw new IOException("remote audio exceeded " + maxBytes + " bytes");
            }
            output.write(buffer, 0, read);
        }
    }

    private URI resolveAceStepAudioUri(String fileReference) {
        String trimmed = fileReference.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return URI.create(trimmed);
        }
        String base = aceStepBaseUrl.replaceAll("/$", "") + "/";
        String relative = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        return URI.create(base).resolve(relative);
    }

    private String buildAceStepAudioFileReference(Path localAudio) {
        return "/v1/audio?path=" + URLEncoder.encode(localAudio.toString(), StandardCharsets.UTF_8);
    }

    private String inferAudioExtension(String fileReference) {
        String lowered = fileReference.toLowerCase();
        if (lowered.contains(".wav")) {
            return ".wav";
        }
        if (lowered.contains(".flac")) {
            return ".flac";
        }
        return ".mp3";
    }

    private String sanitizeFileName(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "ace-step-audio" : sanitized;
    }

    private boolean isStreamableAudioPath(String rawPath) {
        Path audioPath = Paths.get(rawPath).toAbsolutePath().normalize();
        return allowedAudioRoots().stream().anyMatch(audioPath::startsWith)
                && Files.exists(audioPath)
                && Files.isRegularFile(audioPath);
    }

    private List<Path> allowedAudioRoots() {
        return List.of(
                resolveAiRoot().resolve("vendor").resolve("ACE-Step-1.5").toAbsolutePath().normalize(),
                resolveAiRoot().resolve("generated").toAbsolutePath().normalize()
        );
    }

    private String extractAudioFilePath(Map<String, Object> interventionResult) {
        Map<String, Object> aceStepJob = mapValue(interventionResult.get("ace_step_job"));
        List<Map<String, Object>> parsedEntries = listOfMaps(aceStepJob.get("parsed_entries"));
        if (parsedEntries.isEmpty()) {
            return null;
        }

        String file = stringValue(parsedEntries.get(0).get("file"));
        if (file == null || !file.contains("path=")) {
            return null;
        }

        String rawPath = file.substring(file.indexOf("path=") + 5);
        int ampersandIndex = rawPath.indexOf('&');
        if (ampersandIndex >= 0) {
            rawPath = rawPath.substring(0, ampersandIndex);
        }
        return URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
    }

    private boolean isRetryableAceStepFailure(ResponseStatusException error) {
        int statusCode = error.getStatusCode().value();
        return statusCode == HttpStatus.BAD_GATEWAY.value() || statusCode == HttpStatus.GATEWAY_TIMEOUT.value();
    }

    private boolean ensureAceStepServerReady() {
        if (isAceStepReachable()) {
            return true;
        }

        synchronized (aceStepServerLock) {
            if (isAceStepReachable()) {
                return true;
            }
            if (!shouldAutoStartAceStep()) {
                return false;
            }
            if (aceStepServerProcess == null || !aceStepServerProcess.isAlive()) {
                startAceStepServer();
            }
        }

        return waitForAceStepHealth(ACE_STEP_STARTUP_WAIT);
    }

    private boolean restartAceStepServer() {
        if (!shouldAutoStartAceStep()) {
            return false;
        }
        synchronized (aceStepServerLock) {
            if (aceStepServerProcess != null && aceStepServerProcess.isAlive()) {
                aceStepServerProcess.destroyForcibly();
            }
            aceStepServerProcess = null;
            startAceStepServer();
        }
        return waitForAceStepHealth(ACE_STEP_STARTUP_WAIT);
    }

    private void startAceStepServer() {
        if (!shouldAutoStartAceStep()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ACE-Step auto-start is disabled for remote worker mode");
        }
        Path scriptPath = resolveAiRoot().resolve("scripts").resolve("start_acestep_api.sh");
        if (!Files.exists(scriptPath)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ACE-Step start script not found");
        }

        try {
            URI baseUri = aceStepBaseUri();
            String host = baseUri.getHost() != null && !baseUri.getHost().isBlank() ? baseUri.getHost() : "127.0.0.1";
            int port = baseUri.getPort() > 0 ? baseUri.getPort() : 8011;
            Path logPath = resolveAiRoot().resolve(".cache").resolve("noos-acestep-api.log");
            Files.createDirectories(logPath.getParent());

            ProcessBuilder processBuilder = new ProcessBuilder("bash", scriptPath.toString())
                    .directory(resolveAiRoot().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            Map<String, String> env = processBuilder.environment();
            env.put("ACESTEP_HOST", host);
            env.put("ACESTEP_PORT", String.valueOf(port));
            env.put("ACESTEP_NO_INIT", "true");
            env.put("ACESTEP_INIT_LLM", "false");
            env.put("TOKENIZERS_PARALLELISM", "false");
            if (isAppleSiliconMac()) {
                env.putIfAbsent("ACESTEP_LM_BACKEND", "mlx");
                env.putIfAbsent("ACESTEP_OFFLOAD_TO_CPU", "false");
                env.putIfAbsent("ACESTEP_OFFLOAD_DIT_TO_CPU", "false");
                env.putIfAbsent("PYTORCH_ENABLE_MPS_FALLBACK", "1");
            }

            aceStepServerProcess = processBuilder.start();
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to start ACE-Step server", error);
        }
    }

    private boolean waitForAceStepHealth(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isAceStepReachable()) {
                return true;
            }
            try {
                Thread.sleep(600);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean isAceStepReachable() {
        return aceStepHealth() != null;
    }

    private Map<String, Object> aceStepHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aceStepBaseUrl.replaceAll("/$", "") + "/health"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(ACE_STEP_HEALTHCHECK_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private boolean ensureAceStepModelInitialized() {
        Map<String, Object> health = aceStepHealth();
        Map<String, Object> healthData = mapValue(health != null ? health.get("data") : null);
        String expectedModel = normalizedAceStepModelName();
        String expectedLmModel = normalizedAceStepLmModel();
        boolean modelReady = Boolean.TRUE.equals(healthData.get("models_initialized"))
                && expectedModel.equals(stringValue(healthData.get("loaded_model")));
        boolean lmReady = !aceStepUseEnhancedRequest
                || (Boolean.TRUE.equals(healthData.get("llm_initialized"))
                && expectedLmModel.equals(stringValue(healthData.get("loaded_lm_model"))));
        if (modelReady && lmReady) {
            return true;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", expectedModel);
            payload.put("slot", 1);
            payload.put("init_llm", aceStepUseEnhancedRequest);
            if (aceStepUseEnhancedRequest && !expectedLmModel.isBlank()) {
                payload.put("lm_model_path", expectedLmModel);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aceStepBaseUrl.replaceAll("/$", "") + "/v1/init"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(Math.max(120, aceStepTimeoutSec)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "ACE-Step model prewarm failed with HTTP " + response.statusCode()
                );
            }

            Map<String, Object> result = objectMapper.readValue(response.body(), MAP_TYPE);
            Object code = result.get("code");
            if (code instanceof Number number && number.intValue() != 200) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "ACE-Step model prewarm failed: " + stringValue(result.get("error"))
                );
            }
            return true;
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to prewarm ACE-Step model", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ACE-Step model prewarm was interrupted", error);
        }
    }

    private URI aceStepBaseUri() {
        return URI.create(aceStepBaseUrl.replaceAll("/$", ""));
    }

    private boolean shouldAutoStartAceStep() {
        return aceStepAutoStart && !isRemoteAceStepBaseUrl();
    }

    private boolean isRemoteAceStepBaseUrl() {
        String host = aceStepBaseUri().getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.toLowerCase();
        return !List.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(normalized);
    }

    private boolean isAppleSiliconMac() {
        return "Mac OS X".equalsIgnoreCase(System.getProperty("os.name"))
                && "aarch64".equalsIgnoreCase(System.getProperty("os.arch"));
    }

    private String buildAudioProxyUrl(String absolutePath) {
        return UriComponentsBuilder.fromPath("/api/ai/audio")
                .queryParam("path", absolutePath)
                .build()
                .encode()
                .toUriString();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private double numberValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private double rounded(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private int roundedInt(double value) {
        return (int) Math.round(value);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> strings = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String string && !string.isBlank()) {
                strings.add(string.toLowerCase());
            }
        }
        return strings;
    }

    private void accumulatePlanetVotes(Map<String, Integer> votes, List<String> planets) {
        for (String planet : planets) {
            votes.merge(planet, 1, Integer::sum);
        }
    }

    private List<String> topPlanetVotes(Map<String, Integer> votes) {
        return votes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                mapped.add((Map<String, Object>) map);
            }
        }
        return mapped;
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }
}
