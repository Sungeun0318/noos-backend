package com.noos.backend.ai.dto;

import java.util.Map;

public record AiFeedbackParseRequest(
        String feedbackText,
        Integer rating,
        String planet,
        String targetState,
        String measuredState,
        String measuredSource,
        Map<String, Object> currentState
) {
}
