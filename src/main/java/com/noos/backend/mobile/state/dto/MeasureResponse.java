package com.noos.backend.mobile.state.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MeasureResponse(
        String measurementId,
        String stateLabel,
        Map<String, Double> currentState,
        String recommendedPlanet,
        List<String> alternates,
        Double confidence,
        String source,
        WeightInfo weight,
        Instant measuredAt,
        RecognitionDetail recognition
) {
    public record RecognitionDetail(
            String dominantState,
            List<AxisDetail> axes,
            Quality quality,
            Map<String, Double> bands
    ) {
    }

    public record AxisDetail(
            String key,
            double score,
            String level,
            double confidence,
            String rationale
    ) {
    }

    public record Quality(
            boolean usable,
            double score,
            List<String> warnings
    ) {
    }
}
