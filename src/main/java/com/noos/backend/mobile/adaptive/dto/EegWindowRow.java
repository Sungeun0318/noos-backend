package com.noos.backend.mobile.adaptive.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class EegWindowRow {
    private Long id;
    private String adaptiveSessionId;
    private Integer windowIndex;
    private Instant windowStartAt;
    private Instant windowEndAt;
    private Integer windowDurationSec;
    private Long sampleCount;
    private Double sampleRateHz;
    private Double delta;
    private Double theta;
    private Double alpha;
    private Double beta;
    private Double gamma;
    private String dominantBand;
    private Double qualityScore;
    private boolean signalOk;
    private Double focusReadiness;
    private Double stressLoad;
    private Double fatigueRisk;
    private Double relaxationLevel;
    private Double corticalArousal;
    private Double mentalWorkload;
    private String stateLabel;
    private String adaptiveAction;
    private Instant createdAt;
}
