package com.noos.backend.mobile.adaptive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionRow;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionStartRequest;
import com.noos.backend.mobile.adaptive.dto.EegWindowRow;
import com.noos.backend.mobile.adaptive.dto.PauseAdaptiveSessionRequest;
import com.noos.backend.mobile.adaptive.dto.SessionSegmentRow;
import com.noos.backend.mobile.adaptive.mapper.AdaptiveSessionMapper;
import com.noos.backend.mobile.adaptive.mapper.EegWindowMapper;
import com.noos.backend.mobile.adaptive.mapper.SessionSegmentMapper;
import com.noos.backend.mobile.adaptive.service.AdaptiveSessionService;
import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import com.noos.backend.mobile.common.RequestContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdaptiveSessionServiceTest {

    private static final String DEVICE_ID = "dev_adaptive_test";

    @Mock
    private AdaptiveSessionMapper adaptiveSessionMapper;

    @Mock
    private EegWindowMapper eegWindowMapper;

    @Mock
    private SessionSegmentMapper sessionSegmentMapper;

    private AdaptiveSessionService service;

    @BeforeEach
    void setUp() {
        service = new AdaptiveSessionService(adaptiveSessionMapper, eegWindowMapper, sessionSegmentMapper);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void startCreatesAdaptiveSessionAndSeedSegment() {
        RequestContext.setUser(42L, DEVICE_ID);
        assignSeedSegmentId();

        var response = service.start(new AdaptiveSessionStartRequest("eeg", "Mars"), DEVICE_ID);

        ArgumentCaptor<AdaptiveSessionRow> sessionCaptor = ArgumentCaptor.forClass(AdaptiveSessionRow.class);
        ArgumentCaptor<SessionSegmentRow> segmentCaptor = ArgumentCaptor.forClass(SessionSegmentRow.class);
        verify(adaptiveSessionMapper).insert(sessionCaptor.capture());
        verify(sessionSegmentMapper).insert(segmentCaptor.capture());

        AdaptiveSessionRow session = sessionCaptor.getValue();
        assertThat(session.getId()).startsWith("session_");
        assertThat(session.getUserId()).isEqualTo(42L);
        assertThat(session.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(session.getStatus()).isEqualTo("active");
        assertThat(session.getInitialPlanet()).isEqualTo("Mars");
        assertThat(session.getCurrentPlanet()).isEqualTo("Mars");
        assertThat(session.getSeedSource()).isEqualTo("eeg");

        SessionSegmentRow segment = segmentCaptor.getValue();
        assertThat(segment.getAdaptiveSessionId()).isEqualTo(session.getId());
        assertThat(segment.getSegmentIndex()).isZero();
        assertThat(segment.getPlanet()).isEqualTo("Mars");
        assertThat(segment.getStatus()).isEqualTo("pending");
        assertThat(segment.isFallback()).isFalse();
        assertThat(segment.getDurationSec()).isEqualTo(120);

        assertThat(response.sessionId()).isEqualTo(session.getId());
        assertThat(response.status()).isEqualTo("active");
        assertThat(response.seedSegment().segmentId()).isEqualTo(100L);
        assertThat(response.seedSegment().status()).isEqualTo("pending");
    }

    @Test
    void startDefaultsSeedSourceAndPlanet() {
        assignSeedSegmentId();

        service.start(new AdaptiveSessionStartRequest(null, null), DEVICE_ID);

        ArgumentCaptor<AdaptiveSessionRow> sessionCaptor = ArgumentCaptor.forClass(AdaptiveSessionRow.class);
        verify(adaptiveSessionMapper).insert(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getSeedSource()).isEqualTo("none");
        assertThat(sessionCaptor.getValue().getCurrentPlanet()).isEqualTo("Neptune");
    }

    @Test
    void startRejectsInvalidSeedSource() {
        assertThatThrownBy(() -> service.start(new AdaptiveSessionStartRequest("gemma", "Mars"), DEVICE_ID))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void getComposesSessionSegmentsAndRecentWindows() {
        AdaptiveSessionRow session = session("active", DEVICE_ID);
        when(adaptiveSessionMapper.findById("session_adaptive")).thenReturn(session);
        when(sessionSegmentMapper.listSegments("session_adaptive")).thenReturn(List.of(
                segment(1L, 0, "done"),
                segment(2L, 1, "pending")
        ));
        when(eegWindowMapper.listWindows("session_adaptive")).thenReturn(List.of(
                window(1L, 0),
                window(2L, 1),
                window(3L, 2),
                window(4L, 3),
                window(5L, 4),
                window(6L, 5)
        ));

        var response = service.get("session_adaptive", DEVICE_ID);

        assertThat(response.status()).isEqualTo("active");
        assertThat(response.currentSegment().segmentId()).isEqualTo(1L);
        assertThat(response.nextSegment().segmentId()).isEqualTo(2L);
        assertThat(response.segments()).hasSize(2);
        assertThat(response.recentWindows())
                .extracting(window -> window.windowId())
                .containsExactly(2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void pauseResumeAndEndUpdateAllowedStates() {
        when(adaptiveSessionMapper.findById("session_adaptive"))
                .thenReturn(session("active", DEVICE_ID))
                .thenReturn(session("paused", DEVICE_ID))
                .thenReturn(session("active", DEVICE_ID));

        var paused = service.pause("session_adaptive", DEVICE_ID, new PauseAdaptiveSessionRequest("wear_off"));
        var resumed = service.resume("session_adaptive", DEVICE_ID);
        var ended = service.end("session_adaptive", DEVICE_ID);

        assertThat(paused.status()).isEqualTo("paused");
        assertThat(paused.pausedReason()).isEqualTo("wear_off");
        assertThat(resumed.status()).isEqualTo("active");
        assertThat(ended.status()).isEqualTo("ended");

        verify(adaptiveSessionMapper).updateStatus("session_adaptive", "paused", "wear_off", paused.pausedAt(), null);
        verify(adaptiveSessionMapper).updateStatus("session_adaptive", "active", null, null, null);
        verify(adaptiveSessionMapper).updateStatus("session_adaptive", "ended", null, null, ended.endedAt());
    }

    @Test
    void pauseRejectsEndedSession() {
        when(adaptiveSessionMapper.findById("session_adaptive")).thenReturn(session("ended", DEVICE_ID));

        assertThatThrownBy(() -> service.pause("session_adaptive", DEVICE_ID, new PauseAdaptiveSessionRequest("user")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.ADAPTIVE_SESSION_STATE_CONFLICT);
    }

    @Test
    void resumeRequiresPausedSession() {
        when(adaptiveSessionMapper.findById("session_adaptive")).thenReturn(session("active", DEVICE_ID));

        assertThatThrownBy(() -> service.resume("session_adaptive", DEVICE_ID))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.ADAPTIVE_SESSION_STATE_CONFLICT);
    }

    @Test
    void unknownOrForeignSessionReturnsNotFound() {
        when(adaptiveSessionMapper.findById("missing")).thenReturn(null);
        when(adaptiveSessionMapper.findById("foreign")).thenReturn(session("active", "other_device"));

        assertThatThrownBy(() -> service.get("missing", DEVICE_ID))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.ADAPTIVE_SESSION_NOT_FOUND);
        assertThatThrownBy(() -> service.get("foreign", DEVICE_ID))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.ADAPTIVE_SESSION_NOT_FOUND);
    }

    @Test
    void ownerUserCanAccessFromDifferentDevice() {
        AdaptiveSessionRow session = session("active", "original_device");
        session.setUserId(7L);
        RequestContext.setUser(7L, "new_device");
        when(adaptiveSessionMapper.findById("session_adaptive")).thenReturn(session);
        when(sessionSegmentMapper.listSegments("session_adaptive")).thenReturn(List.of());
        when(eegWindowMapper.listWindows("session_adaptive")).thenReturn(List.of());

        var response = service.get("session_adaptive", "new_device");

        assertThat(response.sessionId()).isEqualTo("session_adaptive");
    }

    private AdaptiveSessionRow session(String status, String deviceId) {
        AdaptiveSessionRow row = new AdaptiveSessionRow();
        row.setId("session_adaptive");
        row.setDeviceId(deviceId);
        row.setStatus(status);
        row.setInitialPlanet("Mars");
        row.setCurrentPlanet("Mars");
        row.setSeedSource("eeg");
        row.setStartedAt(Instant.parse("2026-06-07T00:00:00Z"));
        row.setCreatedAt(Instant.parse("2026-06-07T00:00:00Z"));
        return row;
    }

    private void assignSeedSegmentId() {
        doAnswer(invocation -> {
            SessionSegmentRow row = invocation.getArgument(0);
            row.setId(100L);
            return null;
        }).when(sessionSegmentMapper).insert(any(SessionSegmentRow.class));
    }

    private SessionSegmentRow segment(Long id, int index, String status) {
        SessionSegmentRow row = new SessionSegmentRow();
        row.setId(id);
        row.setAdaptiveSessionId("session_adaptive");
        row.setSegmentIndex(index);
        row.setPlanet("Mars");
        row.setStatus(status);
        row.setDurationSec(120);
        row.setCreatedAt(Instant.parse("2026-06-07T00:0" + index + ":00Z"));
        return row;
    }

    private EegWindowRow window(Long id, int index) {
        EegWindowRow row = new EegWindowRow();
        row.setId(id);
        row.setAdaptiveSessionId("session_adaptive");
        row.setWindowIndex(index);
        row.setWindowStartAt(Instant.parse("2026-06-07T00:00:00Z"));
        row.setWindowEndAt(Instant.parse("2026-06-07T00:05:00Z"));
        row.setWindowDurationSec(300);
        row.setSampleCount(76800L);
        row.setSampleRateHz(256.0);
        row.setDelta(0.1);
        row.setTheta(0.2);
        row.setAlpha(0.3);
        row.setBeta(0.2);
        row.setGamma(0.1);
        row.setDominantBand("alpha");
        row.setQualityScore(0.9);
        row.setSignalOk(true);
        row.setFocusReadiness(0.6);
        row.setStressLoad(0.2);
        row.setFatigueRisk(0.1);
        row.setRelaxationLevel(0.7);
        row.setCorticalArousal(0.5);
        row.setMentalWorkload(0.3);
        row.setStateLabel("steady focus");
        row.setAdaptiveAction("none");
        row.setCreatedAt(Instant.parse("2026-06-07T00:05:00Z"));
        return row;
    }
}
