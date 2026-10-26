package com.noos.backend.mobile.lighting.dto;

import jakarta.validation.constraints.NotNull;

public record LightingToggleRequest(
        @NotNull Boolean enabled
) {
}
