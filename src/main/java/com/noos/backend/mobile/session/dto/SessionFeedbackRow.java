package com.noos.backend.mobile.session.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class SessionFeedbackRow {
    private String sessionId;
    private Double musicFit;
    private Double lightingFit;
    private Double focusResult;
    private String memo;
    private Instant createdAt;
}
