package com.noos.backend.mobile.state.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.ai.dto.EegRecognitionRequest;
import com.noos.backend.ai.dto.PlanetRecommendationRequest;
import com.noos.backend.ai.service.NoosAiService;
import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import com.noos.backend.mobile.state.dto.EegInput;
import com.noos.backend.mobile.state.dto.MeasureRequest;
import com.noos.backend.mobile.state.dto.MeasureResponse;
import com.noos.backend.mobile.state.dto.StateMeasurementRow;
import com.noos.backend.mobile.state.dto.SurveyInput;
import com.noos.backend.mobile.state.dto.WeightInfo;
import com.noos.backend.mobile.state.mapper.StateMeasurementMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StateMeasurementService {

    private final StateMeasurementMapper mapper;
    private final NoosAiService noosAiService;
    private final ObjectMapper objectMapper;
    private final double configuredSurveyWeight;
    private final double configuredEegWeight;
    private final double minSignalQuality;

    public StateMeasurementService(StateMeasurementMapper mapper,
                                   NoosAiService noosAiService,
                                   ObjectMapper objectMapper,
                                   @Value("${noos.mobile.measure.weight.survey:0.4}") double configuredSurveyWeight,
                                   @Value("${noos.mobile.measure.weight.eeg:0.6}") double configuredEegWeight,
                                   @Value("${noos.mobile.measure.eeg.min-signal-quality:0.3}") double minSignalQuality) {
        this.mapper = mapper;
        this.noosAiService = noosAiService;
        this.objectMapper = objectMapper;
        this.configuredSurveyWeight = configuredSurveyWeight;
        this.configuredEegWeight = configuredEegWeight;
        this.minSignalQuality = minSignalQuality;
    }

    public MeasureResponse measure(MeasureRequest request, String deviceId) {
        SurveyValues survey = normalize(request.survey());
        Map<String, Double> surveyState = surveyToState(survey);
        Map<String, Double> currentState = surveyState;
        String stateLabel = stateLabel(survey);
        String source = "survey";
        double effectiveSurveyWeight = 1.0;
        double effectiveEegWeight = 0.0;
        Map<String, Object> recognitionResult = Map.of();

        if (passesQualityGate(request.eeg())) {
            recognitionResult = noosAiService.recognizeFromSummary(toEegRecognitionRequest(request.eeg()));
            Map<String, Double> eegState = extractCurrentState(recognitionResult.get("currentState"));
            if (!eegState.isEmpty()) {
                effectiveEegWeight = configuredEegWeight * Math.min(1.0, request.eeg().signalQuality() / 0.8);
                effectiveSurveyWeight = 1.0 - effectiveEegWeight;
                currentState = combineStates(surveyState, eegState, effectiveSurveyWeight, effectiveEegWeight);
                String eegStateLabel = stringValue(recognitionResult.get("stateLabel"));
                if (eegStateLabel != null) {
                    stateLabel = eegStateLabel;
                }
                source = "hybrid";
            }
        }

        Map<String, Object> currentStatePayload = new LinkedHashMap<>(currentState);

        Map<String, Object> recommendation = noosAiService.recommendPlanet(new PlanetRecommendationRequest(
                survey.intentText(),
                null,
                null,
                currentStatePayload,
                null,
                null
        ));

        String recommendedPlanet = firstString(recommendation, "recommendedPlanet", "recommended", "planet");
        if (recommendedPlanet == null) {
            recommendedPlanet = "Neptune";
        }
        List<String> alternates = stringList(firstPresent(recommendation, "alternates", "candidates"));
        Double confidence = doubleValue(recommendation.get("confidence"));

        Instant now = Instant.now();
        String measurementId = "meas_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        StateMeasurementRow row = new StateMeasurementRow();
        row.setId(measurementId);
        row.setDeviceId(deviceId);
        row.setSource(source);
        row.setSurveyJson(writeJson(survey.toMap()));
        row.setEegJson(request.eeg() == null ? null : writeJson(request.eeg()));
        row.setEegDeviceType(request.eeg() == null ? null : request.eeg().deviceType());
        row.setSignalQuality(request.eeg() == null ? null : request.eeg().signalQuality());
        row.setStateLabel(stateLabel);
        row.setCurrentState(writeJson(currentState));
        row.setRecommendedPlanet(recommendedPlanet);
        row.setAlternatesJson(writeJson(alternates));
        row.setConfidence(confidence);
        row.setWeightSurvey(effectiveSurveyWeight);
        row.setWeightEeg(effectiveEegWeight);
        row.setMeasuredAt(now);
        row.setCreatedAt(now);
        mapper.insert(row);

        return new MeasureResponse(
                measurementId,
                stateLabel,
                currentState,
                recommendedPlanet,
                alternates,
                confidence,
                source,
                new WeightInfo(effectiveSurveyWeight, effectiveEegWeight),
                now
        );
    }

    private SurveyValues normalize(SurveyInput survey) {
        return new SurveyValues(
                clamp(defaultValue(survey.focus())),
                clamp(defaultValue(survey.stress())),
                clamp(defaultValue(survey.fatigue())),
                clamp(defaultValue(survey.relaxation())),
                survey.intentText()
        );
    }

    private double defaultValue(Double value) {
        return value == null ? 0.5 : value;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private Map<String, Double> surveyToState(SurveyValues survey) {
        Map<String, Double> state = new LinkedHashMap<>();
        state.put("focus_readiness", survey.focus());
        state.put("stress_load", survey.stress());
        state.put("fatigue_risk", survey.fatigue());
        state.put("relaxation_level", survey.relaxation());
        state.put("cortical_arousal", (survey.focus() + (1.0 - survey.relaxation())) / 2.0);
        state.put("mental_workload", (survey.stress() + survey.fatigue()) / 2.0);
        return state;
    }

    private String stateLabel(SurveyValues survey) {
        if (survey.stress() > 0.7) {
            return "stressed";
        }
        if (survey.fatigue() > 0.7) {
            return "fatigued";
        }
        if (survey.relaxation() > 0.6 && survey.focus() > 0.5) {
            return "calm focus";
        }
        if (survey.focus() > 0.7) {
            return "focused";
        }
        return "neutral";
    }

    private boolean passesQualityGate(EegInput eeg) {
        if (eeg == null || eeg.bands() == null || eeg.bands().isEmpty()
                || eeg.signalQuality() == null || eeg.signalQuality() < minSignalQuality) {
            return false;
        }
        return true;
    }

    private EegRecognitionRequest toEegRecognitionRequest(EegInput eeg) {
        return new EegRecognitionRequest(
                null,
                eeg.deviceType(),
                eeg.measuredAt() == null ? null : eeg.measuredAt().toString(),
                eeg.measurementDurationSec(),
                eeg.sampleRateHz(),
                eeg.sampleCount(),
                dominantBand(eeg.bands()),
                bandValue(eeg, "delta"),
                bandValue(eeg, "theta"),
                bandValue(eeg, "alpha"),
                bandValue(eeg, "beta"),
                bandValue(eeg, "gamma")
        );
    }

    private Double bandValue(EegInput eeg, String key) {
        return eeg.bands() == null ? 0.0 : eeg.bands().getOrDefault(key, 0.0);
    }

    private String dominantBand(Map<String, Double> bands) {
        if (bands == null || bands.isEmpty()) {
            return null;
        }
        return bands.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private Map<String, Double> extractCurrentState(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Double> state = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                Double number = doubleValue(entry.getValue());
                if (number != null) {
                    state.put(key, clamp(number));
                }
            }
        }
        return state;
    }

    private Map<String, Double> combineStates(Map<String, Double> surveyState,
                                              Map<String, Double> eegState,
                                              double surveyWeight,
                                              double eegWeight) {
        Map<String, Double> combined = new LinkedHashMap<>();
        for (String axis : List.of(
                "focus_readiness",
                "stress_load",
                "fatigue_risk",
                "relaxation_level",
                "cortical_arousal",
                "mental_workload")) {
            double surveyValue = surveyState.getOrDefault(axis, 0.5);
            Double eegValue = eegState.get(axis);
            combined.put(axis, eegValue == null ? surveyValue : (surveyValue * surveyWeight) + (eegValue * eegWeight));
        }
        return combined;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ApiException(
                    ErrorCode.INVALID_STATE_MEASUREMENT_PAYLOAD,
                    ErrorCode.INVALID_STATE_MEASUREMENT_PAYLOAD.name(),
                    e
            );
        }
    }

    private String firstString(Map<String, Object> map, String... keys) {
        Object value = firstPresent(map, keys);
        if (value == null) {
            return null;
        }
        String string = String.valueOf(value);
        return string.isBlank() ? null : string;
    }

    private Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Double.parseDouble(string);
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

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> strings = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    strings.add(String.valueOf(item));
                }
            }
            return strings;
        }
        return List.of();
    }

    private record SurveyValues(double focus, double stress, double fatigue, double relaxation, String intentText) {
        private Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("focus", focus);
            values.put("stress", stress);
            values.put("fatigue", fatigue);
            values.put("relaxation", relaxation);
            values.put("intentText", intentText);
            return values;
        }
    }
}
