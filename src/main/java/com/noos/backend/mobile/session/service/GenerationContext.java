package com.noos.backend.mobile.session.service;

import java.util.Map;

public record GenerationContext(
        String planet,
        int durationSec,
        Map<String, Double> currentState,
        String stateLabel,
        String intentText,
        String source,
        boolean lightingEnabled
) {
}
