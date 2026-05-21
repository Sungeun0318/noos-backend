package com.noos.backend.ai.dto;

import java.util.Map;

public record InterventionGenerationRequest(
        String planet,
        Map<String, Object> currentState,
        Map<String, Object> recognitionResult,
        Integer durationSec,
        Integer candidateCountOverride,
        java.util.List<Map<String, Object>> feedbackHistory,
        String memoText,
        Map<String, Object> intentContext
) {
}
