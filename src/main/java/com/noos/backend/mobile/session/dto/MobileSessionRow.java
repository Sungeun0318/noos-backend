package com.noos.backend.mobile.session.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class MobileSessionRow {
    private String id;
    private Long userId;
    private String deviceId;
    private String planet;
    private Integer durationSec;
    private String stateLabel;
    private String currentState;
    private String intentText;
    private String recognitionId;
    private String audioId;
    private String lightingJobId;
    private boolean lightingEnabled;
    private String source;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant deletedAt;
}
