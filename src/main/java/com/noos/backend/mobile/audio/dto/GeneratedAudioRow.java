package com.noos.backend.mobile.audio.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class GeneratedAudioRow {
    private String id;
    private String sessionId;
    private String storageType;
    private String storageRef;
    private String mime;
    private Integer durationSec;
    private Long sizeBytes;
    private String sourceWorker;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
