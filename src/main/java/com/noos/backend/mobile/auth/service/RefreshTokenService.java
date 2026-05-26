package com.noos.backend.mobile.auth.service;

import com.noos.backend.mobile.auth.dto.MobileAuthTokenRow;
import com.noos.backend.mobile.auth.mapper.MobileAuthMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final MobileAuthMapper mobileAuthMapper;
    private final long refreshTtlDays;

    public RefreshTokenService(MobileAuthMapper mobileAuthMapper,
                               @Value("${noos.mobile.auth.refresh-ttl-days:14}") long refreshTtlDays) {
        this.mobileAuthMapper = mobileAuthMapper;
        this.refreshTtlDays = refreshTtlDays;
    }

    public IssuedTokens issue(long userId, String deviceId, String accessJti) {
        String refreshToken = randomRefreshToken();
        MobileAuthTokenRow row = new MobileAuthTokenRow();
        row.setId("maut_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        row.setUserId(userId);
        row.setDeviceId(deviceId);
        row.setAccessJti(accessJti);
        row.setRefreshToken(sha256(refreshToken));
        row.setExpiresAt(Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS));
        row.setCreatedAt(Instant.now());
        mobileAuthMapper.insert(row);
        return new IssuedTokens(row, refreshToken);
    }

    public MobileAuthTokenRow requireActiveRefresh(String refreshToken) {
        MobileAuthTokenRow row = mobileAuthMapper.findActiveByRefreshToken(sha256(refreshToken));
        if (row == null || row.getExpiresAt() == null || row.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return row;
    }

    public boolean isAccessTokenActive(String accessJti) {
        MobileAuthTokenRow row = mobileAuthMapper.findByAccessJti(accessJti);
        return row != null && row.getRevokedAt() == null;
    }

    public void revokeByAccessJti(String accessJti) {
        MobileAuthTokenRow row = mobileAuthMapper.findByAccessJti(accessJti);
        if (row != null) {
            mobileAuthMapper.revokeById(row.getId());
        }
    }

    public void revokeById(String id) {
        mobileAuthMapper.revokeById(id);
    }

    public void touchLastUsed(String id) {
        mobileAuthMapper.touchLastUsed(id);
    }

    String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String randomRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedTokens(MobileAuthTokenRow row, String refreshToken) {
    }
}
