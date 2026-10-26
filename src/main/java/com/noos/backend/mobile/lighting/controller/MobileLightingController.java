package com.noos.backend.mobile.lighting.controller;

import com.noos.backend.lighting.service.WizLightingService;
import com.noos.backend.mobile.lighting.service.MobileLightingCoordinator;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        return lightingCoordinator.stopAll();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return wizLightingService.status();
    }
}
