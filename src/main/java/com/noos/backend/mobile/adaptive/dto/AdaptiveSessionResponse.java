package com.noos.backend.mobile.adaptive.dto;

import java.time.Instant;
import java.util.List;

public record AdaptiveSessionResponse(
        String sessionId,
        String status,
        String initialPlanet,
        String currentPlanet,
        String seedSource,
        String pausedReason,
        SegmentView currentSegment,
        SegmentView nextSegment,
        List<SegmentView> segments,
        List<EegWindowView> recentWindows,
        Instant startedAt,
        Instant pausedAt,
        Instant endedAt,
        Instant createdAt
) {
    public record SegmentView(
            Long segmentId,
            int index,
            String planet,
            String status,
            String audioId,
            boolean fallback,
            int durationSec,
            Instant genStartedAt,
            Instant genReadyAt,
            Instant playedAt,
            Instant createdAt
    ) {
    }

    public record EegWindowView(
            Long windowId,
            int index,
            Instant windowStartAt,
            Instant windowEndAt,
            int durationSec,
            long sampleCount,
            Double sampleRateHz,
            Bands bands,
            String dominantBand,
            Double qualityScore,
            boolean signalOk,
            SixAxis currentState,
            String stateLabel,
            String adaptiveAction,
            Instant createdAt
    ) {
    }

    public record Bands(
            Double delta,
            Double theta,
            Double alpha,
            Double beta,
            Double gamma
    ) {
    }

    public record SixAxis(
            Double focusReadiness,
            Double stressLoad,
            Double fatigueRisk,
            Double relaxationLevel,
            Double corticalArousal,
            Double mentalWorkload
    ) {
    }
}
