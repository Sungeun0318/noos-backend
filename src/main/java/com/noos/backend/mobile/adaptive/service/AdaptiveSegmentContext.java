package com.noos.backend.mobile.adaptive.service;

import java.util.Map;

public record AdaptiveSegmentContext(
        String adaptiveSessionId,
        String planet,
        int durationSec,
        Map<String, Double> sixAxisMap
) {
}
