package com.noos.backend.mobile.session.dto;

import java.time.Instant;

public record FeedbackResponse(boolean ok, Instant savedAt) {
}
