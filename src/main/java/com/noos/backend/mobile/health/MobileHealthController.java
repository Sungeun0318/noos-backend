package com.noos.backend.mobile.health;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.noos.backend.lighting.service.WizLightingService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@RestController
public class MobileHealthController {

    private final String version;
    private final String minAppVersion;
    private final String aceStepBaseUrl;
    private final RestTemplate restTemplate;
    private final WizLightingService wizLightingService;
    private final Cache<String, String> aceStepStatusCache;

    public MobileHealthController(@Value("${noos.mobile.version:dev}") String version,
                                  @Value("${noos.mobile.min-app-version:1.0.0}") String minAppVersion,
                                  @Value("${noos.ai.ace-step.base-url}") String aceStepBaseUrl,
                                  @Value("${noos.mobile.health.ace-step.cache-seconds:10}") int aceStepCacheSeconds,
                                  @Qualifier("mobileHealthRestTemplate") RestTemplate restTemplate,
                                  WizLightingService wizLightingService) {
        this.version = version;
        this.minAppVersion = minAppVersion;
        this.aceStepBaseUrl = trimTrailingSlash(aceStepBaseUrl);
        this.restTemplate = restTemplate;
        this.wizLightingService = wizLightingService;
        this.aceStepStatusCache = Caffeine.newBuilder()
                .expireAfterWrite(Math.max(1, aceStepCacheSeconds), TimeUnit.SECONDS)
                .build();
    }

    @GetMapping("/api/mobile/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("backend", "ok");
        response.put("ai", "unknown");
        response.put("aceStep", aceStepStatusCache.get("status", ignored -> pingAceStep()));
        response.put("lighting", lightingStatus());
        response.put("version", version);
        response.put("minAppVersion", minAppVersion);
        response.put("serverTime", Instant.now().toString());
        return response;
    }

    private String pingAceStep() {
        try {
            ResponseEntity<Void> response = restTemplate.getForEntity(aceStepBaseUrl + "/health", Void.class);
            HttpStatusCode status = response.getStatusCode();
            if (status.is2xxSuccessful()) {
                return "ok";
            }
            if (status.is5xxServerError()) {
                return "down";
            }
            return "degraded";
        } catch (ResourceAccessException e) {
            return "degraded";
        } catch (Exception e) {
            return "down";
        }
    }

    private String lightingStatus() {
        if (!wizLightingService.shouldAutoApply()) {
            return "unknown";
        }
        try {
            Object enabled = wizLightingService.status().get("enabled");
            return Boolean.TRUE.equals(enabled) ? "ok" : "down";
        } catch (Exception e) {
            return "down";
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
