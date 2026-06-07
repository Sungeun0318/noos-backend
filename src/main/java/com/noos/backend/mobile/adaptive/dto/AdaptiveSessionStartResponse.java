package com.noos.backend.mobile.adaptive.dto;

public record AdaptiveSessionStartResponse(
        String sessionId,
        String status,
        SeedSegment seedSegment
) {
    public record SeedSegment(
            Long segmentId,
            int index,
            String status
    ) {
    }
}
