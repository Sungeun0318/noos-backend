package com.noos.backend.mobile.device.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class PushDeviceRow {
    private String id;
    private String deviceId;
    private Long userId;
    private String platform;
    private String provider;
    private String token;
    private String appVersion;
    private String locale;
    private boolean active;
    private Instant lastSeenAt;
    private Instant createdAt;
}
