package com.noos.backend.mobile.auth.controller;

import com.noos.backend.mobile.auth.dto.MeResponse;
import com.noos.backend.mobile.auth.service.MobileAuthService;
import com.noos.backend.mobile.common.RequestContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MobileMeController {

    private final MobileAuthService mobileAuthService;

    public MobileMeController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @GetMapping("/api/mobile/me")
    public MeResponse me(@RequestHeader("x-device-id") String deviceId) {
        return mobileAuthService.me(deviceId, RequestContext.userId());
    }
}
