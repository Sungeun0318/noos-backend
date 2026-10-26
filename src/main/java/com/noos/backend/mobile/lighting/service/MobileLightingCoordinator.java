package com.noos.backend.mobile.lighting.service;

import com.noos.backend.lighting.service.WizLightingService;
import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 모바일 세션 단위 WiZ on/off.
 *
 * <p>{@code NoosAiService.generateIntervention} 은 음악 생성 직후 조명을 자동 적용한다(웹과 동일, HARNESS 2.2 로 수정 불가).
 * 여기서는 그 뒤처리만 한다 — 세션별 {@code lighting_spec} 을 기억해 두고, 끄면 stop(세션 전 조명으로 복구),
 * 다시 켜면 기억한 spec 을 재적용한다.
 *
 * <p>전구는 백엔드 한 대에 묶인 공용 자원이라 "지금 조명을 잡고 있는 세션"(owner) 하나만 추적한다.
 */
@Service
public class MobileLightingCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MobileLightingCoordinator.class);
    private static final int MAX_REMEMBERED_SESSIONS = 32;

    private final WizLightingService wizLightingService;
    private final Map<String, Map<String, Object>> lightingSpecs = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Map<String, Object>> eldest) {
            return size() > MAX_REMEMBERED_SESSIONS;
        }
    };
    private String ownerSessionId;

    public MobileLightingCoordinator(WizLightingService wizLightingService) {
        this.wizLightingService = wizLightingService;
    }

    public boolean isAvailable() {
        return wizLightingService.shouldAutoApply();
    }

    public synchronized boolean isLitBy(String sessionId) {
        return sessionId != null && sessionId.equals(ownerSessionId);
    }

    // keepOn=false 면 자동 적용된 조명을 되돌린다(DEC-008). 유지한 경우에만 jobId 를 돌려준다.
    public synchronized String onGenerated(String sessionId, Map<String, Object> generationResult, boolean keepOn) {
        Map<String, Object> spec = lightingSpecOf(generationResult);
        if (!spec.isEmpty()) {
            lightingSpecs.put(sessionId, spec);
        }
        String startedJobId = startedJobId(generationResult);
        if (keepOn) {
            if (startedJobId != null) {
                ownerSessionId = sessionId;
            }
            return startedJobId;
        }
        if (startedJobId == null && ownerSessionId != null && !ownerSessionId.equals(sessionId)) {
            // 자동 적용이 일어나지 않았으니 다른 세션이 켜 둔 조명을 끌 이유가 없다.
            return null;
        }
        revertAutoApplied(sessionId);
        return null;
    }

    // 생성은 실패했는데 조명은 이미 자동 적용된 경우(ACE-Step 다운 등). 실패한 곡의 spec 은 기억하지 않고
    // 직전에 조명을 잡고 있던 세션(적응형이면 같은 세션의 이전 곡)으로 되돌린다.
    public synchronized void onGenerationFailed(Map<String, Object> generationResult) {
        if (startedJobId(generationResult) == null) {
            return;
        }
        String owner = ownerSessionId;
        if (owner != null && lightingSpecs.containsKey(owner) && turnOnQuietly(owner)) {
            return;
        }
        stopQuietly();
        ownerSessionId = null;
    }

    // 기억한 spec 이 없으면(아직 생성 전이거나 서버 재시작) false — 다음 생성 때 켜진다.
    public synchronized boolean turnOn(String sessionId) {
        if (!isAvailable()) {
            throw new ApiException(ErrorCode.LIGHTING_DISABLED);
        }
        Map<String, Object> spec = lightingSpecs.get(sessionId);
        if (spec == null) {
            return false;
        }
        try {
            wizLightingService.startAlternatingFromPayload(Map.of("lighting_spec", spec));
        } catch (ResponseStatusException error) {
            throw new ApiException(ErrorCode.LIGHTING_DISABLED, reasonOf(error), error);
        }
        ownerSessionId = sessionId;
        return true;
    }

    public synchronized boolean turnOnQuietly(String sessionId) {
        try {
            return turnOn(sessionId);
        } catch (RuntimeException error) {
            log.warn("failed to re-apply lighting for session {}", sessionId, error);
            return false;
        }
    }

    // 다른 세션이 잡고 있는 조명은 그대로 둔다.
    public synchronized void turnOff(String sessionId) {
        if (ownerSessionId != null && !ownerSessionId.equals(sessionId)) {
            return;
        }
        stopQuietly();
        ownerSessionId = null;
    }

    public synchronized void forget(String sessionId) {
        turnOff(sessionId);
        lightingSpecs.remove(sessionId);
    }

    public synchronized Map<String, Object> stopAll() {
        ownerSessionId = null;
        return wizLightingService.stopActiveJob();
    }

    private void revertAutoApplied(String sessionId) {
        String previousOwner = ownerSessionId;
        if (previousOwner != null && !previousOwner.equals(sessionId) && lightingSpecs.containsKey(previousOwner)
                && turnOnQuietly(previousOwner)) {
            return;
        }
        stopQuietly();
        ownerSessionId = null;
    }

    private void stopQuietly() {
        try {
            wizLightingService.stopActiveJob();
        } catch (RuntimeException error) {
            log.warn("failed to stop WiZ lighting", error);
        }
    }

    private Map<String, Object> lightingSpecOf(Map<String, Object> generationResult) {
        Map<String, Object> interventionResult = mapValue(generationResult == null ? null : generationResult.get("interventionResult"));
        return mapValue(interventionResult.get("lighting_spec"));
    }

    private String startedJobId(Map<String, Object> generationResult) {
        Map<String, Object> wizLighting = mapValue(generationResult == null ? null : generationResult.get("wizLighting"));
        Object jobId = wizLighting.get("jobId");
        return jobId == null || jobId.toString().isBlank() ? null : jobId.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String reasonOf(ResponseStatusException error) {
        return error.getReason() != null ? error.getReason() : error.getMessage();
    }
}
