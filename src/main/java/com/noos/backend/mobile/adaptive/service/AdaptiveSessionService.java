package com.noos.backend.mobile.adaptive.service;

import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionResponse;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionRow;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionStartRequest;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionStartResponse;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionStatusResponse;
import com.noos.backend.mobile.adaptive.dto.EegWindowRow;
import com.noos.backend.mobile.adaptive.dto.PauseAdaptiveSessionRequest;
import com.noos.backend.mobile.adaptive.dto.SessionSegmentRow;
import com.noos.backend.mobile.adaptive.mapper.AdaptiveSessionMapper;
import com.noos.backend.mobile.adaptive.mapper.EegWindowMapper;
import com.noos.backend.mobile.adaptive.mapper.SessionSegmentMapper;
import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import com.noos.backend.mobile.common.RequestContext;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AdaptiveSessionService {

    private static final String DEFAULT_PLANET = "Neptune";
    private static final int SEED_SEGMENT_DURATION_SEC = 120;
    private static final int RECENT_WINDOW_LIMIT = 5;
    private static final Set<String> SEED_SOURCES = Set.of("survey", "eeg", "hybrid", "none");
    private static final Set<String> PLANETS = Set.of(
            "Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto"
    );
    private static final Set<String> CURRENT_SEGMENT_STATUSES = Set.of("playing", "ready", "done");
    private static final Set<String> NEXT_SEGMENT_STATUSES = Set.of("pending", "generating");

    private final AdaptiveSessionMapper adaptiveSessionMapper;
    private final EegWindowMapper eegWindowMapper;
    private final SessionSegmentMapper sessionSegmentMapper;

    public AdaptiveSessionService(AdaptiveSessionMapper adaptiveSessionMapper,
                                  EegWindowMapper eegWindowMapper,
                                  SessionSegmentMapper sessionSegmentMapper) {
        this.adaptiveSessionMapper = adaptiveSessionMapper;
        this.eegWindowMapper = eegWindowMapper;
        this.sessionSegmentMapper = sessionSegmentMapper;
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
