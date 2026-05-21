package com.noos.backend.eeg.dto;

public record EegSessionStartResponse(
        Long eegSessionId,
        String status,
        boolean created
) {
}
