package com.noos.backend.mobile.device.controller;

import com.noos.backend.mobile.common.RequestContext;
import com.noos.backend.mobile.device.dto.RegisterPushTokenRequest;
import com.noos.backend.mobile.device.dto.RegisterPushTokenResponse;
import com.noos.backend.mobile.device.service.PushDeviceService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/devices")
public class MobileDeviceController {

    private final PushDeviceService pushDeviceService;

    public MobileDeviceController(PushDeviceService pushDeviceService) {
        this.pushDeviceService = pushDeviceService;
    }

    @PostMapping("/push-token")
    public RegisterPushTokenResponse register(@RequestHeader("x-device-id") String deviceId,
                                              @Valid @RequestBody RegisterPushTokenRequest request) {
        return pushDeviceService.register(deviceId, RequestContext.userId(), request);
    }

    @DeleteMapping("/push-token")
    public Map<String, Boolean> unregister(@RequestHeader("x-device-id") String deviceId) {
        pushDeviceService.unregister(deviceId);
        return Map.of("ok", true);
    }
}
