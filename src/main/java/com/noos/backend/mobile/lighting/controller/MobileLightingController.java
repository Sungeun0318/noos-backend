package com.noos.backend.mobile.lighting.controller;

import com.noos.backend.lighting.service.WizLightingService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/lighting")
public class MobileLightingController {

    private final WizLightingService wizLightingService;

    public MobileLightingController(WizLightingService wizLightingService) {
        this.wizLightingService = wizLightingService;
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        return wizLightingService.stopActiveJob();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return wizLightingService.status();
    }
}
