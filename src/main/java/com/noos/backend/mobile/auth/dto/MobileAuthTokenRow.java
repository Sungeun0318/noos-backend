package com.noos.backend.mobile.auth.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class MobileAuthTokenRow {
    private String id;
    private Long userId;
    private String deviceId;
    private String accessJti;
    private String refreshToken;
    private Instant expiresAt;
    private Instant revokedAt;
    private Instant createdAt;
    private Instant lastUsedAt;
}
