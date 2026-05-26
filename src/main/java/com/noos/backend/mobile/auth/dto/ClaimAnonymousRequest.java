package com.noos.backend.mobile.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ClaimAnonymousRequest(@NotBlank String deviceId) {
}
