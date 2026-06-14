package com.noos.backend.mobile.adaptive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.noos.backend.ai.dto.EegRecognitionRequest;
import com.noos.backend.ai.service.NoosAiService;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionRow;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionStartRequest;
import com.noos.backend.mobile.adaptive.dto.AdaptiveFeedbackRequest;
import com.noos.backend.mobile.adaptive.dto.AdaptiveFeedbackRow;
import com.noos.backend.mobile.adaptive.dto.AdaptiveWindowSubmitRequest;
import com.noos.backend.mobile.adaptive.dto.EegWindowRow;
import com.noos.backend.mobile.adaptive.dto.PauseAdaptiveSessionRequest;
import com.noos.backend.mobile.adaptive.dto.SessionSegmentRow;
import com.noos.backend.mobile.adaptive.mapper.AdaptiveFeedbackMapper;
import com.noos.backend.mobile.adaptive.mapper.AdaptiveSessionMapper;
import com.noos.backend.mobile.adaptive.mapper.EegWindowMapper;
import com.noos.backend.mobile.adaptive.mapper.SessionSegmentMapper;
import com.noos.backend.mobile.adaptive.service.AdaptiveActionResolver;
import com.noos.backend.mobile.adaptive.service.AdaptiveSegmentContext;
import com.noos.backend.mobile.adaptive.service.AdaptiveSegmentWorker;
import com.noos.backend.mobile.adaptive.service.AdaptiveSessionService;
import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import com.noos.backend.mobile.common.RequestContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private AdaptiveFeedbackMapper adaptiveFeedbackMapper;

    @Mock
    private EegWindowMapper eegWindowMapper;

    @Mock
    private SessionSegmentMapper sessionSegmentMapper;

    @Mock
    private AdaptiveSegmentWorker adaptiveSegmentWorker;

    @Mock
    private NoosAiService noosAiService;

    private AdaptiveSessionService service;

    @BeforeEach
    void setUp() {
        lenient().when(noosAiService.recognizeFromSummary(any(EegRecognitionRequest.class)))
                .thenReturn(recognition(
                        0.45555555555555555,
                        0.3111111111111111,
                        0.3055555555555556,
                        0.5777777777777777,
                        0.4388888888888889,
                        0.30833333333333335,
                        "neutral"
                ));
        service = new AdaptiveSessionService(
                adaptiveSessionMapper,
                adaptiveFeedbackMapper,
                eegWindowMapper,
                sessionSegmentMapper,
                noosAiService,
                new AdaptiveActionResolver(),
                adaptiveSegmentWorker,
                300
        );
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

        ArgumentCaptor<AdaptiveSegmentContext> contextCaptor = ArgumentCaptor.forClass(AdaptiveSegmentContext.class);
        verify(adaptiveSegmentWorker).run(org.mockito.ArgumentMatchers.eq(100L), contextCaptor.capture());
        assertThat(contextCaptor.getValue().adaptiveSessionId()).isEqualTo(session.getId());
        assertThat(contextCaptor.getValue().planet()).isEqualTo("Mars");
        assertThat(contextCaptor.getValue().durationSec()).isEqualTo(120);
        assertThat(contextCaptor.getValue().sixAxisMap())
                .containsEntry("focus_readiness", 0.5)
                .containsEntry("stress_load", 0.3)
                .containsEntry("fatigue_risk", 0.2)
                .containsEntry("relaxation_level", 0.6)
                .containsEntry("cortical_arousal", 0.45)
                .containsEntry("mental_workload", 0.25);
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

    @Test
    void submitWindowPersistsSixAxisAndNoneActionForStableState() {
        EegWindowRow previous = window(1L, 0);
        previous.setFocusReadiness(0.45555555555555555);
        previous.setStressLoad(0.3111111111111111);
        previous.setFatigueRisk(0.3055555555555556);
        previous.setRelaxationLevel(0.5777777777777777);
        when(adaptiveSessionMapper.findById("session_adaptive")).thenReturn(session("active", DEVICE_ID));
        when(eegWindowMapper.listWindows("session_adaptive")).thenReturn(List.of(previous));
        when(eegWindowMapper.findLatestWindow("session_adaptive")).thenReturn(previous);
        assignWindowId(200L);

        var response = service.submitWindow("session_adaptive", DEVICE_ID, windowRequest(1, bands(10.0, 20.0, 30.0, 20.0, 10.0)));

        assertThat(response.windowId()).isEqualTo(200L);
        assertThat(response.sixAxis().focusReadiness()).isCloseTo(0.45555555555555555, within(0.0001));
        assertThat(response.sixAxis().stressLoad()).isCloseTo(0.3111111111111111, within(0.0001));
        assertThat(response.sixAxis().fatigueRisk()).isCloseTo(0.3055555555555556, within(0.0001));
        assertThat(response.sixAxis().relaxationLevel()).isCloseTo(0.5777777777777777, within(0.0001));
        assertThat(response.sixAxis().corticalArousal()).isCloseTo(0.4388888888888889, within(0.0001));
        assertThat(response.sixAxis().mentalWorkload()).isCloseTo(0.30833333333333335, within(0.0001));
        assertThat(response.adaptiveAction().type()).isEqualTo("none");
        assertThat(response.nextSegment()).isNull();

        ArgumentCaptor<EegWindowRow> windowCaptor = ArgumentCaptor.forClass(EegWindowRow.class);
        verify(eegWindowMapper).insert(windowCaptor.capture());
        assertThat(windowCaptor.getValue().getAdaptiveAction()).isEqualTo("none");
        assertThat(windowCaptor.getValue().getStateLabel()).isEqualTo("neutral");
        ArgumentCaptor<EegRecognitionRequest> recognitionCaptor = ArgumentCaptor.forClass(EegRecognitionRequest.class);
        verify(noosAiService).recognizeFromSummary(recognitionCaptor.capture());
        assertThat(recognitionCaptor.getValue().dominantBand()).isEqualTo("alpha");
        assertThat(recognitionCaptor.getValue().delta()).isEqualTo(10.0);
        assertThat(recognitionCaptor.getValue().measurementDurationSec()).isEqualTo(300);
        verify(sessionSegmentMapper, never()).insert(any(SessionSegmentRow.class));
    }

    @Test
    void submitWindowCreatesPendingSegmentForCrossfade() {
        when(noosAiService.recognizeFromSummary(any(EegRecognitionRequest.class)))
                .thenReturn(recognition(0.20, 0.80, 0.80, 0.20, 0.50, 0.80, "stressed"));
        when(adaptiveSessionMapper.findById("session_adaptive")).thenReturn(session("active", DEVICE_ID));
        when(eegWindowMapper.listWindows("session_adaptive")).thenReturn(List.of(window(1L, 0)));
        when(eegWindowMapper.findLatestWindow("session_adaptive")).thenReturn(window(1L, 0));
        when(sessionSegmentMapper.listSegments("session_adaptive")).thenReturn(List.of(
                segment(10L, 0, "playing"),
                segment(11L, 1, "ready")
        ));
        assignWindowId(201L);
        assignSegmentId(101L);

        var response = service.submitWindow("session_adaptive", DEVICE_ID, windowRequest(1, bands(0.0, 0.0, 0.0, 1.0, 0.0)));

        assertThat(response.adaptiveAction().type()).isEqualTo("crossfade");
        assertThat(response.adaptiveAction().reason()).isEqualTo("calmer-crossfade");
        assertThat(response.nextSegment().id()).isEqualTo(101L);
        assertThat(response.nextSegment().index()).isEqualTo(2);
        assertThat(response.nextSegment().status()).isEqualTo("pending");

        ArgumentCaptor<SessionSegmentRow> segmentCaptor = ArgumentCaptor.forClass(SessionSegmentRow.class);
        verify(sessionSegmentMapper).insert(segmentCaptor.capture());
        assertThat(segmentCaptor.getValue().getDrivenByWindowId()).isEqualTo(201L);
        assertThat(segmentCaptor.getValue().getPlanet()).isEqualTo("Mars");
        assertThat(segmentCaptor.getValue().getStatus()).isEqualTo("pending");
        assertThat(segmentCaptor.getValue().getDurationSec()).isEqualTo(120);
        assertThat(segmentCaptor.getValue().getParamsJson()).contains("\"adaptiveAction\":\"crossfade\"");

        ArgumentCaptor<AdaptiveSegmentContext> contextCaptor = ArgumentCaptor.forClass(AdaptiveSegmentContext.class);
        verify(adaptiveSegmentWorker).run(org.mockito.ArgumentMatchers.eq(101L), contextCaptor.capture());
        assertThat(contextCaptor.getValue().adaptiveSessionId()).isEqualTo("session_adaptive");
        assertThat(contextCaptor.getValue().planet()).isEqualTo("Mars");
        assertThat(contextCaptor.getValue().sixAxisMap()).containsEntry("stress_load", 0.8);
    }

    @Test
    void submitWindowGatesLowQualityToNone() {
        when(noosAiService.recognizeFromSummary(any(EegRecognitionRequest.class)))
                .thenReturn(recognition(0.20, 0.80, 0.80, 0.20, 0.50, 0.80, "stressed"));
        when(adaptiveSessionMapper.findById("session_adaptive")).thenReturn(session("active", DEVICE_ID));
        when(eegWindowMapper.listWindows("session_adaptive")).thenReturn(List.of(window(1L, 0)));
        when(eegWindowMapper.findLatestWindow("session_adaptive")).thenReturn(window(1L, 0));
        assignWindowId(202L);

        var request = windowRequest(1, bands(0.0, 0.0, 0.0, 1.0, 0.0), false, 0.9);
        var response = service.submitWindow("session_adaptive", DEVICE_ID, request);

        assertThat(response.adaptiveAction().type()).isEqualTo("none");
        assertThat(response.adaptiveAction().reason()).isEqualTo("low-signal-quality");
        assertThat(response.sixAxis().focusReadiness()).isEqualTo(0.6);
        assertThat(response.sixAxis().relaxationLevel()).isEqualTo(0.7);
        verify(sessionSegmentMapper, never()).insert(any(SessionSegmentRow.class));
    }

    @Test
    void submitWindowThrottlesRecentCrossfadeToParameterAdjust() {
        when(noosAiService.recognizeFromSummary(any(EegRecognitionRequest.class)))
                .thenReturn(recognition(0.20, 0.80, 0.80, 0.20, 0.50, 0.80, "stressed"));
        EegWindowRow previous = window(1L, 0);
        EegWindowRow recentCrossfade = window(2L, 1);
        recentCrossfade.setAdaptiveAction("crossfade");
        recentCrossfade.setCreatedAt(Instant.now().minusSeconds(60));
        when(adaptiveSessionMapper.findById("session_adaptive")).thenReturn(session("active", DEVICE_ID));
        when(eegWindowMapper.listWindows("session_adaptive")).thenReturn(List.of(previous, recentCrossfade));
        when(eegWindowMapper.findLatestWindow("session_adaptive")).thenReturn(previous);
        assignWindowId(203L);

        var response = service.submitWindow("session_adaptive", DEVICE_ID, windowRequest(2, bands(0.0, 0.0, 0.0, 1.0, 0.0)));

        assertThat(response.adaptiveAction().type()).isEqualTo("parameter_adjust");
        assertThat(response.adaptiveAction().reason()).isEqualTo("regen-throttled");
        assertThat(response.nextSegment()).isNull();
        verify(sessionSegmentMapper, never()).insert(any(SessionSegmentRow.class));
    }

    @Test
    void submitWindowRejectsForeignSessionAndInvalidBody() {
        when(adaptiveSessionMapper.findById("foreign")).thenReturn(session("active", "other_device"));
        assertThatThrownBy(() -> service.submitWindow("foreign", DEVICE_ID, windowRequest(1, bands(0.1, 0.2, 0.3, 0.2, 0.1))))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.ADAPTIVE_SESSION_NOT_FOUND);

        when(adaptiveSessionMapper.findById("session_adaptive")).thenReturn(session("active", DEVICE_ID));
        assertThatThrownBy(() -> service.submitWindow("session_adaptive", DEVICE_ID, null))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void submitFeedbackUpsertsClampedRow() {
        when(adaptiveSessionMapper.findById("session_adaptive")).thenReturn(session("ended", DEVICE_ID));

        var response = service.submitFeedback("session_adaptive", DEVICE_ID, new AdaptiveFeedbackRequest(
                1.2,
                -0.5,
                0.75,
                "  좋았어요  ",
                false
        ));

        ArgumentCaptor<AdaptiveFeedbackRow> rowCaptor = ArgumentCaptor.forClass(AdaptiveFeedbackRow.class);
        verify(adaptiveFeedbackMapper).upsert(rowCaptor.capture());
        AdaptiveFeedbackRow row = rowCaptor.getValue();
        assertThat(row.getAdaptiveSessionId()).isEqualTo("session_adaptive");
        assertThat(row.getMusicFit()).isEqualTo(1.0);
        assertThat(row.getFocusRelaxHelp()).isZero();
        assertThat(row.getTransitionNatural()).isEqualTo(0.75);
        assertThat(row.getMemo()).isEqualTo("좋았어요");
        assertThat(row.isSkipped()).isFalse();
        assertThat(row.getCreatedAt()).isNotNull();
        assertThat(response.ok()).isTrue();
        assertThat(response.savedAt()).isEqualTo(row.getCreatedAt());
    }

    @Test
    void submitFeedbackStoresSkippedRow() {
        when(adaptiveSessionMapper.findById("session_adaptive")).thenReturn(session("ended", DEVICE_ID));

        service.submitFeedback("session_adaptive", DEVICE_ID, new AdaptiveFeedbackRequest(
                null,
                null,
                null,
                null,
                true
        ));

        ArgumentCaptor<AdaptiveFeedbackRow> rowCaptor = ArgumentCaptor.forClass(AdaptiveFeedbackRow.class);
        verify(adaptiveFeedbackMapper).upsert(rowCaptor.capture());
        assertThat(rowCaptor.getValue().isSkipped()).isTrue();
        assertThat(rowCaptor.getValue().getMusicFit()).isNull();
    }

    @Test
    void submitFeedbackRejectsMissingOrForeignSession() {
        when(adaptiveSessionMapper.findById("missing")).thenReturn(null);
        when(adaptiveSessionMapper.findById("foreign")).thenReturn(session("ended", "other_device"));

        assertThatThrownBy(() -> service.submitFeedback("missing", DEVICE_ID, new AdaptiveFeedbackRequest(
                0.5,
                0.5,
                0.5,
                "",
                false
        )))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.ADAPTIVE_SESSION_NOT_FOUND);
        assertThatThrownBy(() -> service.submitFeedback("foreign", DEVICE_ID, new AdaptiveFeedbackRequest(
                0.5,
                0.5,
                0.5,
                "",
                false
        )))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.ADAPTIVE_SESSION_NOT_FOUND);
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

    private void assignWindowId(Long id) {
        doAnswer(invocation -> {
            EegWindowRow row = invocation.getArgument(0);
            row.setId(id);
            return null;
        }).when(eegWindowMapper).insert(any(EegWindowRow.class));
    }

    private void assignSegmentId(Long id) {
        doAnswer(invocation -> {
            SessionSegmentRow row = invocation.getArgument(0);
            row.setId(id);
            return null;
        }).when(sessionSegmentMapper).insert(any(SessionSegmentRow.class));
    }

    private AdaptiveWindowSubmitRequest windowRequest(int index, AdaptiveWindowSubmitRequest.Bands bands) {
        return windowRequest(index, bands, true, 0.9);
    }

    private AdaptiveWindowSubmitRequest windowRequest(int index,
                                                      AdaptiveWindowSubmitRequest.Bands bands,
                                                      boolean signalOk,
                                                      double qualityScore) {
        return new AdaptiveWindowSubmitRequest(
                index,
                Instant.parse("2026-06-07T00:05:00Z"),
                300,
                76800L,
                256.0,
                bands,
                "alpha",
                qualityScore,
                signalOk
        );
    }

    private AdaptiveWindowSubmitRequest.Bands bands(double delta, double theta, double alpha, double beta, double gamma) {
        return new AdaptiveWindowSubmitRequest.Bands(delta, theta, alpha, beta, gamma);
    }

    private Map<String, Object> recognition(double focus,
                                            double stress,
                                            double fatigue,
                                            double relaxation,
                                            double arousal,
                                            double workload,
                                            String label) {
        return Map.of(
                "currentState", Map.of(
                        "focus_readiness", focus,
                        "stress_load", stress,
                        "fatigue_risk", fatigue,
                        "relaxation_level", relaxation,
                        "cortical_arousal", arousal,
                        "mental_workload", workload
                ),
                "stateLabel", label
        );
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
