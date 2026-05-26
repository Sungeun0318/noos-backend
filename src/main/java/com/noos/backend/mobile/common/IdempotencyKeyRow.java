package com.noos.backend.mobile.common;

import java.time.Instant;
import lombok.Data;

@Data
public class IdempotencyKeyRow {
    private String k;
    private String scope;
    private String deviceId;
    private String responseHash;
    private String responseBody;
    private Instant createdAt;
    private Instant expiresAt;
}
