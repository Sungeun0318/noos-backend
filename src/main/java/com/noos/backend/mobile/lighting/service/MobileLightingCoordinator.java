package com.noos.backend.mobile.lighting.service;

import com.noos.backend.lighting.service.WizLightingService;
import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
 * owner 는 조명 프로그램 길이 + 여유만큼만 유효하다 — 앱이 stop 없이 사라져도 오래된 세션의 색을 되살리지 않는다.
 */
@Service
public class MobileLightingCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MobileLightingCoordinator.class);
    private static final int MAX_REMEMBERED_SESSIONS = 32;
    private static final Duration OWNER_LEASE_GRACE = Duration.ofMinutes(10);
    private static final Duration DEFAULT_PROGRAM_DURATION = Duration.ofMinutes(30);

    private final WizLightingService wizLightingService;
    private final Clock clock;
    private final Map<String, Map<String, Object>> lightingSpecs = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Map<String, Object>> eldest) {
            return size() > MAX_REMEMBERED_SESSIONS;
        }
    };
    // 쓰기는 synchronized 안에서만. 세션 조회·폴링은 UDP 명령이 끝나길 기다리지 않도록 잠금 없이 읽는다.
    private volatile String ownerSessionId;
    private volatile Instant ownerLeaseUntil;

    @Autowired
    public MobileLightingCoordinator(WizLightingService wizLightingService) {
        this(wizLightingService, Clock.systemUTC());
    }

    public MobileLightingCoordinator(WizLightingService wizLightingService, Clock clock) {
        this.wizLightingService = wizLightingService;
        this.clock = clock;
    }

    public boolean isAvailable() {
        return wizLightingService.shouldAutoApply();
    }

    public boolean isLitBy(String sessionId) {
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
                takeOwnership(sessionId, spec);
            }
            return startedJobId;
        }
        if (startedJobId == null && ownerSessionId != null && !ownerSessionId.equals(sessionId)) {
            // 자동 적용이 일어나지 않았으니 다른 세션이 켜 둔 조명을 끌 이유가 없다.
            return null;
        }
        restoreLiveOwnerOrStop(sessionId);
        return null;
    }

    // 생성은 실패했는데 조명은 이미 자동 적용된 경우(ACE-Step 다운 등). 실패한 곡의 spec 은 기억하지 않고
    // 직전에 조명을 잡고 있던 세션(적응형이면 같은 세션의 이전 곡)으로 되돌린다.
    public synchronized void onGenerationFailed(Map<String, Object> generationResult) {
        if (startedJobId(generationResult) == null) {
            return;
        }
        restoreLiveOwnerOrStop(null);
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
            // 503 은 전구·설정 문제, 그 밖(400 등)은 기억한 spec 이 잘못된 서버 쪽 데이터 문제다.
            ErrorCode code = error.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()
                    ? ErrorCode.LIGHTING_DISABLED
                    : ErrorCode.INTERNAL;
            throw new ApiException(code, reasonOf(error), error);
        }
        takeOwnership(sessionId, spec);
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
        clearOwnership();
    }

    public synchronized void forget(String sessionId) {
        turnOff(sessionId);
        lightingSpecs.remove(sessionId);
    }

    public synchronized Map<String, Object> stopAll() {
        clearOwnership();
        return wizLightingService.stopActiveJob();
    }

    private void restoreLiveOwnerOrStop(String generatedSessionId) {
        String previousOwner = ownerSessionId;
        boolean restorable = previousOwner != null
                && !previousOwner.equals(generatedSessionId)
                && ownerLeaseActive()
                && lightingSpecs.containsKey(previousOwner);
        if (restorable && turnOnQuietly(previousOwner)) {
            return;
        }
        stopQuietly();
        clearOwnership();
    }

    private void takeOwnership(String sessionId, Map<String, Object> spec) {
        ownerSessionId = sessionId;
        ownerLeaseUntil = clock.instant().plus(programDuration(spec)).plus(OWNER_LEASE_GRACE);
    }

    private void clearOwnership() {
        ownerSessionId = null;
        ownerLeaseUntil = null;
    }

    private boolean ownerLeaseActive() {
        Instant leaseUntil = ownerLeaseUntil;
        return leaseUntil != null && clock.instant().isBefore(leaseUntil);
    }

    private Duration programDuration(Map<String, Object> spec) {
        long totalSec = 0;
        Object phases = spec == null ? null : spec.get("phases");
        if (phases instanceof List<?> list) {
            for (Object phase : list) {
                if (phase instanceof Map<?, ?> map && map.get("duration_sec") instanceof Number seconds) {
                    totalSec += Math.max(0, seconds.longValue());
                }
            }
        }
        return totalSec > 0 ? Duration.ofSeconds(totalSec) : DEFAULT_PROGRAM_DURATION;
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
