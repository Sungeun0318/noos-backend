package com.noos.backend.mobile.state.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record MeasureRequest(
        @Valid @NotNull SurveyInput survey,
        EegInput eeg
) {
}
