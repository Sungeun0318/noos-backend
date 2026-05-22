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
        Instant measuredAt
) {
}
