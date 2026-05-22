package com.noos.backend.mobile.state.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.ai.dto.PlanetRecommendationRequest;
import com.noos.backend.ai.service.NoosAiService;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StateMeasurementService {

    private final StateMeasurementMapper mapper;
    private final NoosAiService noosAiService;
    private final ObjectMapper objectMapper;

    public StateMeasurementService(StateMeasurementMapper mapper,
                                   NoosAiService noosAiService,
                                   ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.noosAiService = noosAiService;
        this.objectMapper = objectMapper;
    }

    public MeasureResponse measure(MeasureRequest request, String deviceId) {
        SurveyValues survey = normalize(request.survey());
        Map<String, Double> currentState = surveyToState(survey);
        String stateLabel = stateLabel(survey);
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
        row.setSource("survey");
        row.setSurveyJson(writeJson(survey.toMap()));
        row.setEegJson(null);
        row.setEegDeviceType(null);
        row.setSignalQuality(null);
        row.setStateLabel(stateLabel);
        row.setCurrentState(writeJson(currentState));
        row.setRecommendedPlanet(recommendedPlanet);
        row.setAlternatesJson(writeJson(alternates));
        row.setConfidence(confidence);
        row.setWeightSurvey(1.0);
        row.setWeightEeg(0.0);
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
                "survey",
                new WeightInfo(1.0, 0.0),
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_STATE_MEASUREMENT_PAYLOAD", e);
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
