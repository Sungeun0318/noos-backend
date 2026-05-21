package com.noos.backend.ai.dto;

import java.util.Map;

public record StateExplanationRequest(
        String title,
        String stateLabel,
        String summary,
        Map<String, Object> currentState,
        String targetPlanet
) {
}
