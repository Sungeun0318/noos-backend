package com.noos.backend.mobile.session.service;

import com.noos.backend.ai.dto.InterventionGenerationRequest;
import com.noos.backend.ai.service.NoosAiService;
import com.noos.backend.lighting.service.WizLightingService;
import com.noos.backend.mobile.audio.service.AudioRegistryService;
import com.noos.backend.mobile.device.service.NotificationService;
import com.noos.backend.mobile.session.dto.MobileSessionRow;
import com.noos.backend.mobile.session.mapper.MobileSessionMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(GenerationWorker.class);

    private final MobileSessionMapper sessionMapper;
    private final NoosAiService noosAiService;
    private final AudioRegistryService audioRegistry;
    private final WizLightingService wizLightingService;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    public GenerationWorker(MobileSessionMapper sessionMapper,
                            NoosAiService noosAiService,
                            AudioRegistryService audioRegistry,
                            WizLightingService wizLightingService,
                            NotificationService notificationService,
                            MeterRegistry meterRegistry) {
        this.sessionMapper = sessionMapper;
        this.noosAiService = noosAiService;
        this.audioRegistry = audioRegistry;
        this.wizLightingService = wizLightingService;
        this.notificationService = notificationService;
        this.meterRegistry = meterRegistry;
    }

    @Async("generationExecutor")
    public void run(String sessionId, GenerationContext ctx) {
        long startedNanos = System.nanoTime();
        try {
            sessionMapper.updateStatus(sessionId, "generating", Instant.now());

            Map<String, Object> intentContext = ctx.intentText() != null && !ctx.intentText().isBlank()
                    ? Map.of("intentText", ctx.intentText())
                    : Map.of();
            Map<String, Object> currentState = new LinkedHashMap<>();
            if (ctx.currentState() != null) {
                currentState.putAll(ctx.currentState());
            }

            InterventionGenerationRequest request = new InterventionGenerationRequest(
                    ctx.planet(),
                    currentState,
                    null,
                    ctx.durationSec(),
                    null,
                    null,
                    ctx.intentText(),
                    intentContext
            );

            Map<String, Object> result = noosAiService.generateIntervention(request);
            String audioPath = extractAudioPath(result);
            if (audioPath == null || audioPath.isBlank()) {
                throw new IllegalStateException("audio path missing from NoosAiService result");
            }

            Integer audioDuration = integerValue(result.get("audioDurationSec"));
            String audioId = audioRegistry.register(audioPath, sessionId, "audio/mpeg", audioDuration);
            String lightingJobId;
            if (ctx.lightingEnabled()) {
                lightingJobId = extractLightingJobId(result);
            } else {
                stopLightingAfterDisabledSession(sessionId);
                lightingJobId = null;
            }

            recordGenerationDuration(ctx.planet(), "ready", startedNanos);
            sessionMapper.markReady(sessionId, audioId, lightingJobId, Instant.now());
            notifyReady(sessionId);
        } catch (Exception e) {
            log.error("generation failed for {}", sessionId, e);
            String code = isAceStepFailure(e) ? "ACE_STEP_DOWN" : "GENERATION_FAILED";
            recordGenerationDuration(ctx.planet(), "failed", startedNanos);
            meterRegistry.counter("noos.mobile.session.failed.count", "errorCode", code).increment();
            sessionMapper.markFailed(sessionId, code, e.getMessage());
            notifyFailed(sessionId, ctx.planet());
        }
    }

    private void recordGenerationDuration(String planet, String status, long startedNanos) {
        Timer.builder("noos.mobile.session.generation.duration")
                .tag("planet", metricValue(planet))
                .tag("status", status)
                .register(meterRegistry)
                .record(System.nanoTime() - startedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    private String metricValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private void notifyReady(String sessionId) {
        try {
            MobileSessionRow row = sessionMapper.findById(sessionId);
            if (row == null) {
                log.warn("push skipped: session not found after ready {}", sessionId);
                return;
            }
            notificationService.notifySessionReady(row.getDeviceId(), row.getUserId(), sessionId, row.getPlanet());
        } catch (Exception pushErr) {
            log.warn("ready push failed after session marked ready {}", sessionId, pushErr);
        }
    }

    private void notifyFailed(String sessionId, String fallbackPlanet) {
        try {
            MobileSessionRow row = sessionMapper.findById(sessionId);
            if (row == null) {
                log.warn("push skipped: session not found after failed {}", sessionId);
                return;
            }
            String planet = row.getPlanet() == null ? fallbackPlanet : row.getPlanet();
            notificationService.notifySessionFailed(row.getDeviceId(), row.getUserId(), sessionId, planet);
        } catch (Exception pushErr) {
            log.warn("failed push failed after session marked failed {}", sessionId, pushErr);
        }
    }

    String extractAudioPath(Map<String, Object> result) {
        String topLevelAudioUrl = extractPathFromAudioReference(stringValue(result.get("audioUrl")));
        if (topLevelAudioUrl != null) {
            return topLevelAudioUrl;
        }

        Map<String, Object> interventionResult = mapValue(result.get("interventionResult"));
        String path = extractPathFromAceStepEntries(interventionResult);
        if (path != null) {
            return path;
        }

        for (String key : List.of("audio_file", "file_path", "audio_path", "local_file_path")) {
            String direct = extractPathFromAudioReference(stringValue(interventionResult.get(key)));
            if (direct != null) {
                return direct;
            }
        }
        return null;
    }

    String extractLightingJobId(Map<String, Object> result) {
        Map<String, Object> wizLighting = mapValue(result.get("wizLighting"));
        return stringValue(wizLighting.get("jobId"));
    }

    private void stopLightingAfterDisabledSession(String sessionId) {
        try {
            wizLightingService.stopActiveJob();
        } catch (Exception stopErr) {
            log.warn("failed to stop lighting after lighting_enabled=false session {}", sessionId, stopErr);
        }
    }

    private String extractPathFromAceStepEntries(Map<String, Object> interventionResult) {
        Map<String, Object> aceStepJob = mapValue(interventionResult.get("ace_step_job"));
        List<Map<String, Object>> entries = listOfMaps(aceStepJob.get("parsed_entries"));
        if (entries.isEmpty()) {
            return null;
        }

        return extractPathFromAudioReference(stringValue(entries.get(0).get("file")));
    }

    private String extractPathFromAudioReference(String reference) {
        if (reference == null) {
            return null;
        }
        if (!reference.contains("path=")) {
            if (reference.startsWith("http://") || reference.startsWith("https://")) {
                return null;
            }
            return reference;
        }

        String rawPath = reference.substring(reference.indexOf("path=") + 5);
        int ampersandIndex = rawPath.indexOf('&');
        if (ampersandIndex >= 0) {
            rawPath = rawPath.substring(0, ampersandIndex);
        }
        return URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
    }

    private boolean isAceStepFailure(Exception e) {
        if (e instanceof ResponseStatusException responseStatusException) {
            HttpStatusCode status = responseStatusException.getStatusCode();
            return status.is5xxServerError();
        }
        return false;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Integer.parseInt(string);
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String string = String.valueOf(value);
        return string.isBlank() ? null : string;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }
}
