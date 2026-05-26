package com.noos.backend.mobile.device.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterPushTokenRequest(
        @NotBlank String platform,
        @NotBlank String provider,
        @NotBlank String token,
        String appVersion,
        String locale
) {
}
