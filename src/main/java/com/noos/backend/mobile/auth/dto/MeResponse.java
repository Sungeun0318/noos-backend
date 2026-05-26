package com.noos.backend.mobile.auth.dto;

public record MeResponse(
        String mode,
        String deviceId,
        AuthUserView user
) {
}
