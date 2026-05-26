package com.noos.backend.mobile.auth.dto;

public record AuthResponse(
        AuthUserView user,
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
