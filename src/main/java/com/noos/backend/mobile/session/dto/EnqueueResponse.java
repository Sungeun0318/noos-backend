package com.noos.backend.mobile.session.dto;

import java.time.Instant;

public record EnqueueResponse(
        String sessionId,
        String status,
        String planet,
        int durationSec,
        int estimatedReadyInSec,
        int pollAfterMs,
        Instant createdAt
) {
}
