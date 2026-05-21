package com.noos.backend.ai.dto;

public record EegRecognitionRequest(
        Long eegSessionId,
        String deviceType,
        String measuredAt,
        Integer measurementDurationSec,
        Integer sampleRateHz,
        Integer sampleCount,
        String dominantBand,
        Double delta,
        Double theta,
        Double alpha,
        Double beta,
        Double gamma
) {
}
