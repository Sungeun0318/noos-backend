package com.noos.backend.mobile.adaptive.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class SessionSegmentRow {
    private Long id;
    private String adaptiveSessionId;
    private Integer segmentIndex;
    private Long drivenByWindowId;
    private String planet;
    private String paramsJson;
    private String audioId;
    private String status;
    private boolean fallback;
    private Instant genStartedAt;
    private Instant genReadyAt;
    private Instant playedAt;
    private Integer durationSec;
    private String errorCode;
    private Instant createdAt;
}
