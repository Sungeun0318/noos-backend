package com.noos.backend.mobile.adaptive.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class AdaptiveFeedbackRow {
    private String adaptiveSessionId;
    private Double musicFit;
    private Double focusRelaxHelp;
    private Double transitionNatural;
    private String memo;
    private boolean skipped;
    private Instant createdAt;
}
