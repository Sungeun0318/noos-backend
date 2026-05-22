package com.noos.backend.mobile.state.dto;

import java.time.Instant;
import java.util.Map;

public record EegInput(
        String deviceType,
        String deviceId,
        Instant measuredAt,
        Integer measurementDurationSec,
        Integer sampleRateHz,
        Integer sampleCount,
        Double signalQuality,
        Map<String, Double> bands
) {
}
