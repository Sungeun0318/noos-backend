package com.noos.backend.mobile.adaptive.dto;

import java.time.Instant;

public record AdaptiveSessionStatusResponse(
        String sessionId,
        String status,
        String pausedReason,
        Instant pausedAt,
        Instant endedAt
) {
}
