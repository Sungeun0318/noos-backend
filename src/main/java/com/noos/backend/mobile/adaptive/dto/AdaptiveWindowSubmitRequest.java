package com.noos.backend.mobile.adaptive.dto;

import java.time.Instant;

public record AdaptiveWindowSubmitRequest(
        Integer windowIndex,
        Instant windowStartAt,
        Integer windowDurationSec,
        Long sampleCount,
        Double sampleRateHz,
        Bands bands,
        String dominantBand,
        Double qualityScore,
        Boolean signalOk
) {
    public record Bands(
            Double delta,
            Double theta,
            Double alpha,
            Double beta,
            Double gamma
    ) {
    }
}
