package com.noos.backend.mobile.common;

import com.noos.backend.mobile.common.mapper.IdempotencyKeyMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 64;

    private final IdempotencyKeyMapper mapper;

    public IdempotencyService(IdempotencyKeyMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<String> tryCachedResponse(String key, String scope, String deviceId) {
        if (isBlank(key)) {
            return Optional.empty();
        }
        IdempotencyKeyRow row = mapper.findByKey(storageKey(key, scope));
        if (row == null) {
            return Optional.empty();
        }
        if (!deviceId.equals(row.getDeviceId())) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        Instant expiresAt = row.getExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            return Optional.empty();
        }
        return Optional.ofNullable(row.getResponseBody());
    }

    public void storeResponse(String key, String scope, String deviceId, String body) {
        if (isBlank(key)) {
            return;
        }
        Instant now = Instant.now();
        IdempotencyKeyRow row = new IdempotencyKeyRow();
        row.setK(storageKey(key, scope));
        row.setScope(scope);
        row.setDeviceId(deviceId);
        row.setResponseHash(sha256(body));
        row.setResponseBody(body);
        row.setCreatedAt(now);
        row.setExpiresAt(now.plus(24, ChronoUnit.HOURS));
        mapper.store(row);
    }

    public int deleteExpired() {
        return mapper.deleteExpired();
    }

    private String storageKey(String key, String scope) {
        String trimmed = key.trim();
        String prefixed = scope + ":" + trimmed;
        if (prefixed.length() <= MAX_KEY_LENGTH) {
            return prefixed;
        }
        return scope + ":" + sha256(trimmed).substring(0, MAX_KEY_LENGTH - scope.length() - 1);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
