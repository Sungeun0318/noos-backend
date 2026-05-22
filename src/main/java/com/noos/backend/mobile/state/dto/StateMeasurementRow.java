package com.noos.backend.mobile.state.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class StateMeasurementRow {
    private String id;
    private Long userId;
    private String deviceId;
    private String source;
    private String surveyJson;
    private String eegJson;
    private String eegDeviceType;
    private Double signalQuality;
    private String stateLabel;
    private String currentState;
    private String recommendedPlanet;
    private String alternatesJson;
    private Double confidence;
    private Double weightSurvey;
    private Double weightEeg;
    private Instant measuredAt;
    private Instant createdAt;
}
