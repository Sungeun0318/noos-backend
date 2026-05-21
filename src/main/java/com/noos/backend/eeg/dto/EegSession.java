package com.noos.backend.eeg.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EegSession {
    private Long eegSessionId;
    private Long userId;
    private String deviceType;
    private String status;
    private LocalDateTime measuredAt;
    private LocalDateTime createdAt;
}
