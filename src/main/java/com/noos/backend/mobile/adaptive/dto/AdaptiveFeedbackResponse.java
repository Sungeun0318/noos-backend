package com.noos.backend.mobile.adaptive.dto;

import java.time.Instant;

public record AdaptiveFeedbackResponse(boolean ok, Instant savedAt) {
}
