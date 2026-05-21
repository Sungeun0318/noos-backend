package com.noos.backend.eeg.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.ai.dto.EegRecognitionRequest;
import com.noos.backend.ai.service.NoosAiService;
import com.noos.backend.eeg.dto.EegRawChunk;
import com.noos.backend.eeg.dto.EegResult;
import com.noos.backend.eeg.dto.EegSession;
import com.noos.backend.eeg.dto.EegSessionStartRequest;
import com.noos.backend.eeg.dto.EegSessionStartResponse;
import com.noos.backend.eeg.mapper.EegMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EegAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(EegAnalysisService.class);
    private static final String ANALYSIS_VERSION = "recognition-v1";
    private static final List<String> CONFIDENCE_AXES = List.of(
            "focus_readiness",
            "stress_load",
            "relaxation_level"
    );

    private final NoosAiService noosAiService;
    private final EegMapper eegMapper;
    private final ObjectMapper objectMapper;
    private final EegRawChunkService eegRawChunkService;

    public EegAnalysisService(
            NoosAiService noosAiService,
            EegMapper eegMapper,
            ObjectMapper objectMapper,
            EegRawChunkService eegRawChunkService
    ) {
        this.noosAiService = noosAiService;
        this.eegMapper = eegMapper;
        this.objectMapper = objectMapper;
        this.eegRawChunkService = eegRawChunkService;
    }

    public Map<String, Object> analyzeAndPersist(EegRecognitionRequest request, Long sessionUserId) {
        if (sessionUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login session is required for EEG save.");
        }

        EegSession eegSession = resolveAnalysisSession(request, sessionUserId);

        try {
            eegMapper.updateEegSessionStatus(eegSession.getEegSessionId(), "PROCESSING");
            List<Map<String, Object>> rawReadings = loadRawReadings(eegSession.getEegSessionId());
            Map<String, Object> response = noosAiService.recognize(request, rawReadings);
            EegResult eegResult = createEegResult(eegSession.getEegSessionId(), request, response);

            eegMapper.insertEegResult(eegResult);
            eegMapper.updateEegSessionStatus(eegSession.getEegSessionId(), "COMPLETED");

            Map<String, Object> enrichedResponse = new LinkedHashMap<>(response);
            enrichedResponse.put("eegSessionId", eegSession.getEegSessionId());
            enrichedResponse.put("eegResultId", eegResult.getEegResultId());
            enrichedResponse.put("saved", true);
            return enrichedResponse;
        } catch (RuntimeException error) {
            markSessionFailed(eegSession.getEegSessionId(), error);
            throw error;
        }
    }

    public EegSessionStartResponse startSession(EegSessionStartRequest request, Long sessionUserId) {
        if (sessionUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login session is required for EEG session start.");
        }

        EegSession eegSession = new EegSession();
        eegSession.setUserId(sessionUserId);
        eegSession.setDeviceType(hasText(request.deviceType()) ? request.deviceType().trim() : "Muse S Athena");
        eegSession.setStatus("COLLECTING");
        eegSession.setMeasuredAt(parseMeasuredAt(request.measuredAt()));
        eegMapper.insertEegSession(eegSession);

        return new EegSessionStartResponse(eegSession.getEegSessionId(), eegSession.getStatus(), true);
    }

    private EegSession resolveAnalysisSession(EegRecognitionRequest request, Long sessionUserId) {
        if (request.eegSessionId() != null) {
            EegSession existing = eegMapper.selectEegSessionById(request.eegSessionId());
            if (existing == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EEG session was not found.");
            }
            if (existing.getUserId() == null || !existing.getUserId().equals(sessionUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "EEG session does not belong to the current user.");
            }
            return existing;
        }

        EegSession eegSession = createEegSession(request, sessionUserId);
        eegMapper.insertEegSession(eegSession);
        return eegSession;
    }

    private EegSession createEegSession(EegRecognitionRequest request, Long sessionUserId) {
        EegSession eegSession = new EegSession();
        eegSession.setUserId(sessionUserId);
        eegSession.setDeviceType(hasText(request.deviceType()) ? request.deviceType().trim() : "Muse S Athena");
        eegSession.setStatus("PROCESSING");
        eegSession.setMeasuredAt(parseMeasuredAt(request.measuredAt()));
        return eegSession;
    }

    private EegResult createEegResult(Long eegSessionId, EegRecognitionRequest request, Map<String, Object> response) {
        Map<String, Object> recognitionResult = mapValue(response.get("recognitionResult"));
        Map<String, Object> currentState = mapValue(response.get("currentState"));
        Map<String, Object> stateProfile = mapValue(recognitionResult.get("state_profile"));
        Map<String, Object> quality = mapValue(recognitionResult.get("quality"));
        Map<String, Object> inputSummary = mapValue(recognitionResult.get("input_summary"));

        EegResult eegResult = new EegResult();
        eegResult.setEegSessionId(eegSessionId);
        eegResult.setDelta(safeNumber(request.delta()));
        eegResult.setTheta(safeNumber(request.theta()));
        eegResult.setAlpha(safeNumber(request.alpha()));
        eegResult.setBeta(safeNumber(request.beta()));
        eegResult.setGamma(safeNumber(request.gamma()));
        eegResult.setDominantBand(hasText(request.dominantBand()) ? request.dominantBand().trim() : null);
        eegResult.setStateKey(stringValue(stateProfile.get("dominant_state")));
        eegResult.setStateLabel(firstText(response.get("stateLabel"), nestedText(recognitionResult, "state_profile", "label")));
        eegResult.setConfidence(resolveOverallConfidence(recognitionResult));
        eegResult.setQualityScore(roundToThree(clamp01(readNumber(quality.get("score"), 0.0))));
        eegResult.setFeatureSource(stringValue(inputSummary.get("feature_source")));
        eegResult.setFocusScore(resolveAxisScore("focus_readiness", currentState, recognitionResult));
        eegResult.setRelaxScore(resolveAxisScore("relaxation_level", currentState, recognitionResult));
        eegResult.setStressScore(resolveAxisScore("stress_load", currentState, recognitionResult));
        eegResult.setMentalWorkloadScore(resolveAxisScore("mental_workload", currentState, recognitionResult));
        eegResult.setFatigueRiskScore(resolveAxisScore("fatigue_risk", currentState, recognitionResult));
        eegResult.setCorticalArousalScore(resolveAxisScore("cortical_arousal", currentState, recognitionResult));
        eegResult.setAnalysisVersion(ANALYSIS_VERSION);
        eegResult.setRawAiResponseJson(toJson(response));
        return eegResult;
    }

    private LocalDateTime parseMeasuredAt(String measuredAt) {
        if (!hasText(measuredAt)) {
            return LocalDateTime.now();
        }

        try {
            return OffsetDateTime.parse(measuredAt.trim())
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(measuredAt.trim());
        } catch (DateTimeParseException ignored) {
        }

        return LocalDateTime.now();
    }

    private List<Map<String, Object>> loadRawReadings(Long eegSessionId) {
        if (eegSessionId == null || eegSessionId <= 0) {
            return List.of();
        }

        List<EegRawChunk> chunks = new ArrayList<>(eegRawChunkService.findChunks(eegSessionId));
        if (chunks.isEmpty()) {
            return List.of();
        }

        chunks.sort(Comparator.comparing(EegRawChunk::getChunkIndex, Comparator.nullsLast(Integer::compareTo)));

        List<Map<String, Object>> readings = new ArrayList<>();
        for (EegRawChunk chunk : chunks) {
            readings.addAll(decodeChunkSamples(chunk));
        }
        return readings;
    }

    private List<Map<String, Object>> decodeChunkSamples(EegRawChunk chunk) {
        try {
            List<List<Double>> samples = objectMapper.readValue(
                    chunk.getSamplesJson(),
                    new TypeReference<List<List<Double>>>() {}
            );

            List<Map<String, Object>> readings = new ArrayList<>();
            long startOffsetMs = chunk.getStartOffsetMs() != null ? chunk.getStartOffsetMs() : 0L;
            for (List<Double> sample : samples) {
                if (sample == null || sample.size() < 5) {
                    continue;
                }

                Double localOffsetMs = sample.get(0);
                Double tp9 = sample.get(1);
                Double af7 = sample.get(2);
                Double af8 = sample.get(3);
                Double tp10 = sample.get(4);

                if (localOffsetMs == null || tp9 == null || af7 == null || af8 == null || tp10 == null) {
                    continue;
                }

                Map<String, Object> reading = new LinkedHashMap<>();
                reading.put("timestamp", startOffsetMs + localOffsetMs);
                reading.put("source", "backend-eeg-raw-chunk");
                reading.put("channels", Map.of(
                        "TP9", tp9,
                        "AF7", af7,
                        "AF8", af8,
                        "TP10", tp10
                ));
                readings.add(reading);
            }
            return readings;
        } catch (JsonProcessingException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to decode EEG raw chunks.", error);
        }
    }

    private Double resolveOverallConfidence(Map<String, Object> recognitionResult) {
        double qualityScore = readNumber(mapValue(recognitionResult.get("quality")).get("score"), 0.0);
        Map<String, Object> dimensions = mapValue(mapValue(recognitionResult.get("state_profile")).get("dimensions"));

        double confidenceSum = 0.0;
        int confidenceCount = 0;
        for (String axis : CONFIDENCE_AXES) {
            Map<String, Object> axisPayload = mapValue(dimensions.get(axis));
            Object confidence = axisPayload.get("confidence");
            if (confidence instanceof Number number) {
                confidenceSum += number.doubleValue();
                confidenceCount += 1;
            }
        }

        double axisConfidence = confidenceCount > 0 ? confidenceSum / confidenceCount : 0.0;
        double resolved = qualityScore > 0 && axisConfidence > 0
                ? (qualityScore + axisConfidence) / 2.0
                : Math.max(qualityScore, axisConfidence);

        return roundToThree(clamp01(resolved));
    }

    private Double resolveAxisScore(String axisKey, Map<String, Object> currentState, Map<String, Object> recognitionResult) {
        Object currentStateValue = currentState.get(axisKey);
        if (currentStateValue instanceof Number number) {
            return roundToThree(clamp01(number.doubleValue()));
        }

        Map<String, Object> dimensions = mapValue(mapValue(recognitionResult.get("state_profile")).get("dimensions"));
        Map<String, Object> axisPayload = mapValue(dimensions.get(axisKey));
        return roundToThree(clamp01(readNumber(axisPayload.get("score"), 0.0)));
    }

    private void markSessionFailed(Long eegSessionId, RuntimeException error) {
        try {
            eegMapper.updateEegSessionStatus(eegSessionId, "FAILED");
        } catch (RuntimeException updateError) {
            logger.error("Failed to mark EEG session {} as FAILED", eegSessionId, updateError);
        }

        logger.error("Failed to analyze or persist EEG session {}", eegSessionId, error);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            logger.warn("Failed to serialize EEG AI response for persistence", error);
            return null;
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> next = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    next.put(key, entry.getValue());
                }
            }
            return next;
        }
        return Map.of();
    }

    private String nestedText(Map<String, Object> root, String outerKey, String innerKey) {
        return stringValue(mapValue(root.get(outerKey)).get(innerKey));
    }

    private String firstText(Object preferred, String fallback) {
        String preferredText = stringValue(preferred);
        return hasText(preferredText) ? preferredText : fallback;
    }

    private String stringValue(Object value) {
        return value instanceof String string && hasText(string) ? string.trim() : null;
    }

    private double readNumber(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private Double safeNumber(Double value) {
        return value != null ? roundToThree(value) : 0.0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private Double roundToThree(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
