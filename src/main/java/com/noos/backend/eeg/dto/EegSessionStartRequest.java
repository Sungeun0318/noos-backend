package com.noos.backend.eeg.dto;

public record EegSessionStartRequest(
        String deviceType,
        String measuredAt
) {
}
