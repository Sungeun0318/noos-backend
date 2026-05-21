package com.noos.backend.ai.dto;

public record DeviceTroubleshootRequest(
        String issueText,
        String stage,
        String browser,
        String deviceType
) {
}
