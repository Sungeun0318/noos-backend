package com.noos.backend.mobile.adaptive.dto;

public record AdaptiveFeedbackRequest(
        Double musicFit,
        Double focusRelaxHelp,
        Double transitionNatural,
        String memo,
        Boolean skipped
) {
}
