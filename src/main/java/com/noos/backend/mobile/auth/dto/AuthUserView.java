package com.noos.backend.mobile.auth.dto;

public record AuthUserView(
        Long userId,
        String loginId,
        String displayName
) {
}
