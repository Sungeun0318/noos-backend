package com.noos.backend.mobile.state.dto;

public record SurveyInput(
        Double focus,
        Double stress,
        Double fatigue,
        Double relaxation,
        String intentText
) {
}
