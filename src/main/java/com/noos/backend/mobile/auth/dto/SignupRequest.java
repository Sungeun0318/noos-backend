package com.noos.backend.mobile.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SignupRequest(
        @NotBlank String loginId,
        @NotBlank String password,
        String displayName
) {
}
