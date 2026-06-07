package com.noos.backend.mobile.adaptive.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class AdaptiveSessionRow {
    private String id;
    private Long userId;
    private String deviceId;
    private String status;
    private String initialPlanet;
    private String currentPlanet;
    private String pausedReason;
    private String seedSource;
    private Instant startedAt;
    private Instant pausedAt;
    private Instant endedAt;
    private Instant createdAt;
}
