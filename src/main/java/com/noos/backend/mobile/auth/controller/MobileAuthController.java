package com.noos.backend.mobile.auth.controller;

import com.noos.backend.mobile.auth.dto.AuthResponse;
import com.noos.backend.mobile.auth.dto.LoginRequest;
import com.noos.backend.mobile.auth.dto.RefreshRequest;
import com.noos.backend.mobile.auth.dto.SignupRequest;
import com.noos.backend.mobile.auth.service.MobileAuthService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthController {

    private final MobileAuthService mobileAuthService;

    public MobileAuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @PostMapping("/signup")
    public AuthResponse signup(@RequestHeader("x-device-id") String deviceId,
                               @Valid @RequestBody SignupRequest request) {
        return mobileAuthService.signup(deviceId, request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestHeader("x-device-id") String deviceId,
                              @Valid @RequestBody LoginRequest request) {
        return mobileAuthService.login(deviceId, request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return mobileAuthService.refresh(request);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        mobileAuthService.logout(authorization);
        return Map.of("ok", true);
    }
}
