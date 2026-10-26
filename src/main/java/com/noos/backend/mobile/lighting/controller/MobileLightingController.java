package com.noos.backend.mobile.lighting.controller;

import com.noos.backend.lighting.service.WizLightingService;
import com.noos.backend.mobile.lighting.dto.LightingStopRequest;
import com.noos.backend.mobile.lighting.service.MobileLightingCoordinator;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/lighting")
public class MobileLightingController {

    private final WizLightingService wizLightingService;
    private final MobileLightingCoordinator lightingCoordinator;

    public MobileLightingController(WizLightingService wizLightingService,
                                    MobileLightingCoordinator lightingCoordinator) {
        this.wizLightingService = wizLightingService;
        this.lightingCoordinator = lightingCoordinator;
    }

    // sessionId 를 주면 그 세션이 잡고 있는 조명만 끈다(이미 다른 세션이 잡았으면 그대로 둔다).
    @PostMapping("/stop")
    public Map<String, Object> stop(@RequestBody(required = false) LightingStopRequest request) {
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            return lightingCoordinator.stopAll();
        }
        lightingCoordinator.turnOff(request.sessionId());
        return wizLightingService.status();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return wizLightingService.status();
    }
}
