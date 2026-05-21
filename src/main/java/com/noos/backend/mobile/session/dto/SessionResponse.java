package com.noos.backend.mobile.session.dto;

import java.time.Instant;
import java.util.Map;

public record SessionResponse(
        String sessionId,
        String status,
        String planet,
        int durationSec,
        String stateLabel,
        Map<String, Double> currentState,
        AudioInfo audio,
        LightingInfo lighting,
        Object summary,
        Object progress,
        Object error,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
    public record AudioInfo(String audioId, Integer durationSec) {
    }

    public record LightingInfo(String jobId) {
    }
}
