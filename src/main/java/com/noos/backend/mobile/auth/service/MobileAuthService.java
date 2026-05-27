package com.noos.backend.mobile.auth.service;

import com.noos.backend.mobile.auth.dto.AuthResponse;
import com.noos.backend.mobile.auth.dto.AuthUserView;
import com.noos.backend.mobile.auth.dto.LoginRequest;
import com.noos.backend.mobile.auth.dto.MeResponse;
import com.noos.backend.mobile.auth.dto.MobileAuthTokenRow;
import com.noos.backend.mobile.auth.dto.MobileUserRow;
import com.noos.backend.mobile.auth.dto.RefreshRequest;
import com.noos.backend.mobile.auth.dto.SignupRequest;
import com.noos.backend.mobile.auth.mapper.UserDirectoryMapper;
import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class MobileAuthService {

    private final UserDirectoryMapper userDirectoryMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final ClaimService claimService;
    private final long expiresIn;

    public MobileAuthService(UserDirectoryMapper userDirectoryMapper,
                             PasswordEncoder passwordEncoder,
                             JwtService jwtService,
                             RefreshTokenService refreshTokenService,
                             ClaimService claimService,
                             @Value("${noos.mobile.auth.access-ttl-min:15}") long accessTtlMin) {
        this.userDirectoryMapper = userDirectoryMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.claimService = claimService;
        this.expiresIn = accessTtlMin * 60;
    }

    public AuthResponse signup(String deviceId, SignupRequest request) {
        if (userDirectoryMapper.findLocalUserByLoginId(request.loginId()) != null) {
            throw new ApiException(ErrorCode.LOGIN_ID_TAKEN);
        }

        MobileUserRow row = new MobileUserRow();
        row.setLoginId(request.loginId());
        row.setDisplayName(request.displayName());
        row.setPasswordHash(passwordEncoder.encode(request.password()));
        userDirectoryMapper.insertLocalUser(row);

        if (request.claimDeviceId() != null && !request.claimDeviceId().isBlank()) {
            claimService.claim(row.getUserId(), request.claimDeviceId());
        }

        return issueAuth(row, deviceId);
    }

    public AuthResponse login(String deviceId, LoginRequest request) {
        MobileUserRow row = userDirectoryMapper.findLocalUserByLoginId(request.loginId());
        if (row == null || row.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), row.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issueAuth(row, deviceId);
    }

    public AuthResponse refresh(RefreshRequest request) {
        MobileAuthTokenRow oldRow = refreshTokenService.requireActiveRefresh(request.refreshToken());
        refreshTokenService.revokeById(oldRow.getId());
        refreshTokenService.touchLastUsed(oldRow.getId());

        MobileUserRow user = userDirectoryMapper.findById(oldRow.getUserId());
        if (user == null) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issueAuth(user, oldRow.getDeviceId());
    }

    public void logout(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        if (token == null) {
            return;
        }
        try {
            Claims claims = jwtService.parse(token);
            refreshTokenService.revokeByAccessJti(claims.getId());
        } catch (JwtException | IllegalArgumentException ignored) {
            // Invalid access tokens are already treated as guest mode by JwtAuthenticationFilter.
        }
    }

    public MeResponse me(String deviceId, Long userId) {
        if (userId == null) {
            return new MeResponse("guest", deviceId, null);
        }
        MobileUserRow user = userDirectoryMapper.findById(userId);
        if (user == null) {
            return new MeResponse("guest", deviceId, null);
        }
        return new MeResponse("authed", deviceId, toView(user));
    }

    public boolean isAccessTokenActive(String accessJti) {
        return refreshTokenService.isAccessTokenActive(accessJti);
    }

    private AuthResponse issueAuth(MobileUserRow user, String deviceId) {
        String accessJti = "jti_" + UUID.randomUUID().toString().replace("-", "");
        String accessToken = jwtService.issueAccess(user.getUserId(), deviceId, accessJti);
        RefreshTokenService.IssuedTokens issued = refreshTokenService.issue(user.getUserId(), deviceId, accessJti);
        return new AuthResponse(toView(user), accessToken, issued.refreshToken(), expiresIn);
    }

    private AuthUserView toView(MobileUserRow row) {
        return new AuthUserView(row.getUserId(), row.getLoginId(), row.getDisplayName());
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring(7);
    }
}
