package com.noos.backend.mobile.adaptive.service;

import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionResponse;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionRow;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionStartRequest;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionStartResponse;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionStatusResponse;
import com.noos.backend.mobile.adaptive.dto.AdaptiveWindowSubmitRequest;
import com.noos.backend.mobile.adaptive.dto.AdaptiveWindowSubmitResponse;
import com.noos.backend.mobile.adaptive.dto.EegWindowRow;
import com.noos.backend.mobile.adaptive.dto.PauseAdaptiveSessionRequest;
import com.noos.backend.mobile.adaptive.dto.SessionSegmentRow;
import com.noos.backend.mobile.adaptive.mapper.AdaptiveSessionMapper;
import com.noos.backend.mobile.adaptive.mapper.EegWindowMapper;
import com.noos.backend.mobile.adaptive.mapper.SessionSegmentMapper;
import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import com.noos.backend.mobile.common.RequestContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdaptiveSessionService {

    private static final String DEFAULT_PLANET = "Neptune";
    private static final int SEED_SEGMENT_DURATION_SEC = 120;
    private static final int RECENT_WINDOW_LIMIT = 5;
    private static final Map<String, Double> NEUTRAL_SEED_STATE = Map.of(
            "focus_readiness", 0.5,
            "stress_load", 0.3,
            "fatigue_risk", 0.2,
            "relaxation_level", 0.6,
            "cortical_arousal", 0.45,
            "mental_workload", 0.25
    );
    private static final Set<String> SEED_SOURCES = Set.of("survey", "eeg", "hybrid", "none");
    private static final Set<String> PLANETS = Set.of(
            "Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto"
    );
    private static final Set<String> CURRENT_SEGMENT_STATUSES = Set.of("playing", "ready", "done");
    private static final Set<String> NEXT_SEGMENT_STATUSES = Set.of("pending", "generating");

    private final AdaptiveSessionMapper adaptiveSessionMapper;
    private final EegWindowMapper eegWindowMapper;
    private final SessionSegmentMapper sessionSegmentMapper;
    private final AdaptiveEegStateMapper adaptiveEegStateMapper;
    private final AdaptiveActionResolver adaptiveActionResolver;
    private final AdaptiveSegmentWorker adaptiveSegmentWorker;
    private final Duration minRegenInterval;

    public AdaptiveSessionService(AdaptiveSessionMapper adaptiveSessionMapper,
                                  EegWindowMapper eegWindowMapper,
                                  SessionSegmentMapper sessionSegmentMapper,
                                  AdaptiveEegStateMapper adaptiveEegStateMapper,
                                  AdaptiveActionResolver adaptiveActionResolver,
                                  AdaptiveSegmentWorker adaptiveSegmentWorker,
                                  @Value("${noos.mobile.adaptive.min-regen-interval-sec:300}") long minRegenIntervalSec) {
        this.adaptiveSessionMapper = adaptiveSessionMapper;
        this.eegWindowMapper = eegWindowMapper;
        this.sessionSegmentMapper = sessionSegmentMapper;
        this.adaptiveEegStateMapper = adaptiveEegStateMapper;
        this.adaptiveActionResolver = adaptiveActionResolver;
        this.adaptiveSegmentWorker = adaptiveSegmentWorker;
        this.minRegenInterval = Duration.ofSeconds(Math.max(0L, minRegenIntervalSec));
    }

    public AdaptiveSessionStartResponse start(AdaptiveSessionStartRequest request, String deviceId) {
        Instant now = Instant.now();
        String seedSource = normalizeSeedSource(request == null ? null : request.seedSource());
        String planet = normalizePlanet(request == null ? null : request.planetHint());
        String sessionId = generateSessionId();

        AdaptiveSessionRow session = new AdaptiveSessionRow();
        session.setId(sessionId);
        session.setUserId(RequestContext.userId());
        session.setDeviceId(deviceId);
        session.setStatus("active");
        session.setInitialPlanet(planet);
        session.setCurrentPlanet(planet);
        session.setSeedSource(seedSource);
        session.setStartedAt(now);
        session.setCreatedAt(now);
        adaptiveSessionMapper.insert(session);

        SessionSegmentRow seed = new SessionSegmentRow();
        seed.setAdaptiveSessionId(sessionId);
        seed.setSegmentIndex(0);
        seed.setPlanet(planet);
        seed.setStatus("pending");
        seed.setFallback(false);
        seed.setDurationSec(SEED_SEGMENT_DURATION_SEC);
        seed.setCreatedAt(now);
        sessionSegmentMapper.insert(seed);
        adaptiveSegmentWorker.run(seed.getId(), new AdaptiveSegmentContext(
                sessionId,
                planet,
                SEED_SEGMENT_DURATION_SEC,
                NEUTRAL_SEED_STATE
        ));

        return new AdaptiveSessionStartResponse(
                sessionId,
                "active",
                new AdaptiveSessionStartResponse.SeedSegment(seed.getId(), 0, "pending")
        );
    }

    public AdaptiveSessionResponse get(String sessionId, String deviceId) {
        AdaptiveSessionRow session = findVisibleSession(sessionId, deviceId);
        List<SessionSegmentRow> segments = sessionSegmentMapper.listSegments(sessionId);
        List<EegWindowRow> windows = eegWindowMapper.listWindows(sessionId);
        return toResponse(session, segments, recentWindows(windows));
    }

    public AdaptiveSessionStatusResponse pause(String sessionId, String deviceId, PauseAdaptiveSessionRequest request) {
        AdaptiveSessionRow session = findVisibleSession(sessionId, deviceId);
        requireStatus(session, "active", "pause");
        Instant now = Instant.now();
        String reason = normalizePauseReason(request == null ? null : request.reason());
        adaptiveSessionMapper.updateStatus(sessionId, "paused", reason, now, null);
        return new AdaptiveSessionStatusResponse(sessionId, "paused", reason, now, null);
    }

    public AdaptiveSessionStatusResponse resume(String sessionId, String deviceId) {
        AdaptiveSessionRow session = findVisibleSession(sessionId, deviceId);
        requireStatus(session, "paused", "resume");
        adaptiveSessionMapper.updateStatus(sessionId, "active", null, null, null);
        return new AdaptiveSessionStatusResponse(sessionId, "active", null, null, null);
    }

    public AdaptiveSessionStatusResponse end(String sessionId, String deviceId) {
        AdaptiveSessionRow session = findVisibleSession(sessionId, deviceId);
        if (!"active".equals(session.getStatus()) && !"paused".equals(session.getStatus())) {
            throw stateConflict(session, "end");
        }
        Instant now = Instant.now();
        adaptiveSessionMapper.updateStatus(sessionId, "ended", null, null, now);
        return new AdaptiveSessionStatusResponse(sessionId, "ended", null, null, now);
    }

    public AdaptiveWindowSubmitResponse submitWindow(String sessionId,
                                                     String deviceId,
                                                     AdaptiveWindowSubmitRequest request) {
        AdaptiveSessionRow session = findVisibleSession(sessionId, deviceId);
        requireStatus(session, "active", "submit window");
        validateWindowRequest(request);

        Instant now = Instant.now();
        List<EegWindowRow> windows = eegWindowMapper.listWindows(sessionId);
        EegWindowRow previousWindow = eegWindowMapper.findLatestWindow(sessionId);
        AdaptiveWindowSubmitResponse.SixAxis measuredSixAxis = adaptiveEegStateMapper.fromBands(request.bands());
        AdaptiveWindowSubmitResponse.SixAxis sixAxis = shouldHoldPreviousState(request)
                ? sixAxisFromPrevious(previousWindow)
                : measuredSixAxis;
        AdaptiveWindowSubmitResponse.AdaptiveAction action = adaptiveActionResolver.resolve(
                sixAxis,
                previousWindow,
                windows,
                request.signalOk(),
                request.qualityScore(),
                session.getCurrentPlanet(),
                now,
                minRegenInterval
        );

        EegWindowRow window = new EegWindowRow();
        window.setAdaptiveSessionId(sessionId);
        window.setWindowIndex(request.windowIndex());
        window.setWindowStartAt(request.windowStartAt());
        window.setWindowEndAt(request.windowStartAt().plusSeconds(request.windowDurationSec()));
        window.setWindowDurationSec(request.windowDurationSec());
        window.setSampleCount(request.sampleCount());
        window.setSampleRateHz(request.sampleRateHz());
        window.setDelta(request.bands().delta());
        window.setTheta(request.bands().theta());
        window.setAlpha(request.bands().alpha());
        window.setBeta(request.bands().beta());
        window.setGamma(request.bands().gamma());
        window.setDominantBand(normalizeDominantBand(request.dominantBand()));
        window.setQualityScore(request.qualityScore());
        window.setSignalOk(request.signalOk());
        window.setFocusReadiness(sixAxis.focusReadiness());
        window.setStressLoad(sixAxis.stressLoad());
        window.setFatigueRisk(sixAxis.fatigueRisk());
        window.setRelaxationLevel(sixAxis.relaxationLevel());
        window.setCorticalArousal(sixAxis.corticalArousal());
        window.setMentalWorkload(sixAxis.mentalWorkload());
        window.setStateLabel(adaptiveEegStateMapper.stateLabel(sixAxis));
        window.setAdaptiveAction(action.type());
        window.setCreatedAt(now);
        eegWindowMapper.insert(window);

        AdaptiveWindowSubmitResponse.NextSegment nextSegment = null;
        if ("crossfade".equals(action.type())) {
            SessionSegmentRow segment = createPendingSegment(session, window.getId(), now, action);
            adaptiveSegmentWorker.run(segment.getId(), new AdaptiveSegmentContext(
                    session.getId(),
                    session.getCurrentPlanet(),
                    segment.getDurationSec(),
                    sixAxisMap(sixAxis)
            ));
            nextSegment = new AdaptiveWindowSubmitResponse.NextSegment(
                    segment.getId(),
                    segment.getSegmentIndex(),
                    segment.getStatus()
            );
        }

        return new AdaptiveWindowSubmitResponse(window.getId(), sixAxis, action, nextSegment);
    }

    private AdaptiveSessionRow findVisibleSession(String sessionId, String deviceId) {
        AdaptiveSessionRow row = adaptiveSessionMapper.findById(sessionId);
        if (row == null || !owns(row, deviceId, RequestContext.userId())) {
            throw new ApiException(ErrorCode.ADAPTIVE_SESSION_NOT_FOUND);
        }
        return row;
    }

    private boolean owns(AdaptiveSessionRow row, String deviceId, Long userId) {
        return deviceId.equals(row.getDeviceId()) || (userId != null && userId.equals(row.getUserId()));
    }

    private void requireStatus(AdaptiveSessionRow session, String expected, String action) {
        if (!expected.equals(session.getStatus())) {
            throw stateConflict(session, action);
        }
    }

    private ApiException stateConflict(AdaptiveSessionRow session, String action) {
        return new ApiException(
                ErrorCode.ADAPTIVE_SESSION_STATE_CONFLICT,
                "Cannot " + action + " adaptive session " + session.getId() + " from status " + session.getStatus()
        );
    }

    private AdaptiveSessionResponse toResponse(AdaptiveSessionRow session,
                                               List<SessionSegmentRow> segments,
                                               List<EegWindowRow> recentWindows) {
        List<AdaptiveSessionResponse.SegmentView> segmentViews = segments.stream()
                .map(this::toSegmentView)
                .toList();
        return new AdaptiveSessionResponse(
                session.getId(),
                session.getStatus(),
                session.getInitialPlanet(),
                session.getCurrentPlanet(),
                session.getSeedSource(),
                session.getPausedReason(),
                currentSegment(segmentViews),
                nextSegment(segmentViews),
                segmentViews,
                recentWindows.stream().map(this::toWindowView).toList(),
                session.getStartedAt(),
                session.getPausedAt(),
                session.getEndedAt(),
                session.getCreatedAt()
        );
    }

    private AdaptiveSessionResponse.SegmentView currentSegment(List<AdaptiveSessionResponse.SegmentView> segments) {
        return segments.stream()
                .filter(segment -> CURRENT_SEGMENT_STATUSES.contains(segment.status()))
                .max(Comparator.comparingInt(AdaptiveSessionResponse.SegmentView::index))
                .orElseGet(() -> segments.stream()
                        .min(Comparator.comparingInt(AdaptiveSessionResponse.SegmentView::index))
                        .orElse(null));
    }

    private AdaptiveSessionResponse.SegmentView nextSegment(List<AdaptiveSessionResponse.SegmentView> segments) {
        return segments.stream()
                .filter(segment -> NEXT_SEGMENT_STATUSES.contains(segment.status()))
                .max(Comparator.comparingInt(AdaptiveSessionResponse.SegmentView::index))
                .orElse(null);
    }

    private AdaptiveSessionResponse.SegmentView toSegmentView(SessionSegmentRow row) {
        return new AdaptiveSessionResponse.SegmentView(
                row.getId(),
                row.getSegmentIndex(),
                row.getPlanet(),
                row.getStatus(),
                row.getAudioId(),
                row.isFallback(),
                row.getDurationSec(),
                row.getGenStartedAt(),
                row.getGenReadyAt(),
                row.getPlayedAt(),
                row.getCreatedAt()
        );
    }

    private AdaptiveSessionResponse.EegWindowView toWindowView(EegWindowRow row) {
        return new AdaptiveSessionResponse.EegWindowView(
                row.getId(),
                row.getWindowIndex(),
                row.getWindowStartAt(),
                row.getWindowEndAt(),
                row.getWindowDurationSec(),
                row.getSampleCount(),
                row.getSampleRateHz(),
                new AdaptiveSessionResponse.Bands(row.getDelta(), row.getTheta(), row.getAlpha(), row.getBeta(), row.getGamma()),
                row.getDominantBand(),
                row.getQualityScore(),
                row.isSignalOk(),
                new AdaptiveSessionResponse.SixAxis(
                        row.getFocusReadiness(),
                        row.getStressLoad(),
                        row.getFatigueRisk(),
                        row.getRelaxationLevel(),
                        row.getCorticalArousal(),
                        row.getMentalWorkload()
                ),
                row.getStateLabel(),
                row.getAdaptiveAction(),
                row.getCreatedAt()
        );
    }

    private List<EegWindowRow> recentWindows(List<EegWindowRow> windows) {
        int from = Math.max(0, windows.size() - RECENT_WINDOW_LIMIT);
        return windows.subList(from, windows.size());
    }

    private SessionSegmentRow createPendingSegment(AdaptiveSessionRow session,
                                                   Long windowId,
                                                   Instant now,
                                                   AdaptiveWindowSubmitResponse.AdaptiveAction action) {
        int nextIndex = sessionSegmentMapper.listSegments(session.getId()).stream()
                .map(SessionSegmentRow::getSegmentIndex)
                .filter(index -> index != null)
                .max(Integer::compareTo)
                .map(index -> index + 1)
                .orElse(0);

        SessionSegmentRow segment = new SessionSegmentRow();
        segment.setAdaptiveSessionId(session.getId());
        segment.setSegmentIndex(nextIndex);
        segment.setDrivenByWindowId(windowId);
        segment.setPlanet(session.getCurrentPlanet());
        segment.setParamsJson(actionParamsJson(action));
        segment.setStatus("pending");
        segment.setFallback(false);
        segment.setDurationSec(SEED_SEGMENT_DURATION_SEC);
        segment.setCreatedAt(now);
        sessionSegmentMapper.insert(segment);
        return segment;
    }

    private String actionParamsJson(AdaptiveWindowSubmitResponse.AdaptiveAction action) {
        return "{\"adaptiveAction\":\"" + action.type()
                + "\",\"reason\":\"" + action.reason()
                + "\",\"volumeScale\":" + action.volumeScale()
                + "}";
    }

    private Map<String, Double> sixAxisMap(AdaptiveWindowSubmitResponse.SixAxis sixAxis) {
        Map<String, Double> state = new LinkedHashMap<>();
        state.put("focus_readiness", sixAxis.focusReadiness());
        state.put("stress_load", sixAxis.stressLoad());
        state.put("fatigue_risk", sixAxis.fatigueRisk());
        state.put("relaxation_level", sixAxis.relaxationLevel());
        state.put("cortical_arousal", sixAxis.corticalArousal());
        state.put("mental_workload", sixAxis.mentalWorkload());
        return state;
    }

    private boolean shouldHoldPreviousState(AdaptiveWindowSubmitRequest request) {
        return Boolean.FALSE.equals(request.signalOk()) || request.qualityScore() < 0.35;
    }

    private AdaptiveWindowSubmitResponse.SixAxis sixAxisFromPrevious(EegWindowRow previousWindow) {
        if (previousWindow == null) {
            return new AdaptiveWindowSubmitResponse.SixAxis(0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
        }
        return new AdaptiveWindowSubmitResponse.SixAxis(
                clamp01(previousWindow.getFocusReadiness()),
                clamp01(previousWindow.getStressLoad()),
                clamp01(previousWindow.getFatigueRisk()),
                clamp01(previousWindow.getRelaxationLevel()),
                clamp01(previousWindow.getCorticalArousal()),
                clamp01(previousWindow.getMentalWorkload())
        );
    }

    private double clamp01(Double value) {
        if (value == null) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private void validateWindowRequest(AdaptiveWindowSubmitRequest request) {
        if (request == null
                || request.windowIndex() == null
                || request.windowIndex() < 0
                || request.windowStartAt() == null
                || request.windowDurationSec() == null
                || request.windowDurationSec() <= 0
                || request.sampleCount() == null
                || request.sampleCount() <= 0
                || request.sampleRateHz() == null
                || request.sampleRateHz() <= 0.0
                || request.signalOk() == null
                || request.qualityScore() == null
                || request.qualityScore() < 0.0
                || request.qualityScore() > 1.0
                || request.bands() == null
                || !validBand(request.bands().delta())
                || !validBand(request.bands().theta())
                || !validBand(request.bands().alpha())
                || !validBand(request.bands().beta())
                || !validBand(request.bands().gamma())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "invalid adaptive window payload");
        }
    }

    private boolean validBand(Double value) {
        return value != null && value >= 0.0 && value <= 1.0 && Double.isFinite(value);
    }

    private String normalizeDominantBand(String dominantBand) {
        return dominantBand == null || dominantBand.isBlank() ? "unknown" : dominantBand.trim();
    }

    private String normalizeSeedSource(String seedSource) {
        String value = seedSource == null || seedSource.isBlank() ? "none" : seedSource.trim();
        if (!SEED_SOURCES.contains(value)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "invalid seedSource");
        }
        return value;
    }

    private String normalizePlanet(String planetHint) {
        if (planetHint == null || planetHint.isBlank()) {
            return DEFAULT_PLANET;
        }
        String value = planetHint.trim();
        if (!PLANETS.contains(value)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "invalid planetHint");
        }
        return value;
    }

    private String normalizePauseReason(String reason) {
        return reason == null || reason.isBlank() ? "user" : reason.trim();
    }

    private String generateSessionId() {
        return "session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
