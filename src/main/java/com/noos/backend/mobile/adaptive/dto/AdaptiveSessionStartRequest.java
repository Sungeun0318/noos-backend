package com.noos.backend.mobile.adaptive.dto;

public record AdaptiveSessionStartRequest(
        String seedSource,
        String planetHint
) {
}
