package com.noos.backend.ai.dto;

import java.util.Map;

public record SessionCoachRequest(
        String planet,
        String intentText,
        Map<String, Object> recommendation,
        Integer recommendedDurationSec
) {
}
