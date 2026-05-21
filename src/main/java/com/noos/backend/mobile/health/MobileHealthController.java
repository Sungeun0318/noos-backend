package com.noos.backend.mobile.health;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MobileHealthController {

    private final String version;

    public MobileHealthController(@Value("${noos.mobile.version:dev}") String version) {
        this.version = version;
    }

    @GetMapping("/api/mobile/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("backend", "ok");
        response.put("ai", "unknown");
        response.put("aceStep", "unknown");
        response.put("lighting", "unknown");
        response.put("version", version);
        response.put("minAppVersion", "1.0.0");
        response.put("serverTime", Instant.now().toString());
        return response;
    }
}
