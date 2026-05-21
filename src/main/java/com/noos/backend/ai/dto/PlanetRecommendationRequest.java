package com.noos.backend.ai.dto;

import java.util.List;
import java.util.Map;

public record PlanetRecommendationRequest(
        String intentText,
        String desiredOutcome,
        String memoText,
        Map<String, Object> currentState,
        List<Map<String, Object>> feedbackHistory,
        Integer requestedDurationSec
) {
}
