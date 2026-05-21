package com.noos.backend.mobile.session.dto;

public record FeedbackRequest(
        Double musicFit,
        Double lightingFit,
        Double focusResult,
        String memo
) {
}
