package com.noos.backend.mobile.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Map;

public record EnqueueRequest(
        @NotBlank String planet,
        @Positive int durationSec,
        Map<String, Double> currentState,
        String stateLabel,
        String intentText,
        String source,
        boolean lightingEnabled,
        String idempotencyKey,
        String measurementId
) {
}
