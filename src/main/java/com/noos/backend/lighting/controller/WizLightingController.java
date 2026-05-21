package com.noos.backend.lighting.controller;

import com.noos.backend.lighting.service.WizLightingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/lighting/wiz")
public class WizLightingController {

    private final WizLightingService wizLightingService;

    public WizLightingController(WizLightingService wizLightingService) {
        this.wizLightingService = wizLightingService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return wizLightingService.status();
    }

    @GetMapping("/devices")
    public Map<String, Object> devices() {
        return wizLightingService.discoverConfiguredDevices();
    }

    @PostMapping("/apply-plan")
    public Map<String, Object> applyPlan(@RequestBody Map<String, Object> payload) {
        return wizLightingService.startAlternatingFromPayload(payload);
    }

    @PostMapping("/test-alternate")
    public Map<String, Object> testAlternate(@RequestBody Map<String, Object> payload) {
        return wizLightingService.startTestAlternate(payload);
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        return wizLightingService.stopActiveJob();
    }
}
