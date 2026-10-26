package com.noos.backend.mobile.lighting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.noos.backend.lighting.service.WizLightingService;
import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import com.noos.backend.mobile.lighting.service.MobileLightingCoordinator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MobileLightingCoordinatorTest {

    private static final Map<String, Object> MARS_SPEC = Map.of(
            "phases", List.of(Map.of("name", "settle", "duration_sec", 600, "primary_hex", "#d26a4a", "secondary_hex", "#4c86ff"))
    );
    private static final Map<String, Object> VENUS_SPEC = Map.of(
            "phases", List.of(Map.of("name", "settle", "primary_hex", "#f2c49b", "secondary_hex", "#ffe6c7"))
    );

    @Mock
    private WizLightingService wizLightingService;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-26T09:00:00Z"));

    private MobileLightingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new MobileLightingCoordinator(wizLightingService, clock);
    }

    @Test
    void keepsAutoAppliedLightingWhenSessionWantsIt() {
        String jobId = coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), true);

        assertThat(jobId).isEqualTo("job_a");
        assertThat(coordinator.isLitBy("session_a")).isTrue();
        verify(wizLightingService, never()).stopActiveJob();
    }

    @Test
    void revertsAutoAppliedLightingWhenSessionDoesNotWantIt() {
        String jobId = coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), false);

        assertThat(jobId).isNull();
        assertThat(coordinator.isLitBy("session_a")).isFalse();
        verify(wizLightingService).stopActiveJob();
    }

    @Test
    void restoresPreviousOwnerInsteadOfStoppingWhenAnotherSessionOverwroteIt() {
        when(wizLightingService.shouldAutoApply()).thenReturn(true);
        coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), true);

        coordinator.onGenerated("session_b", generated(VENUS_SPEC, "job_b"), false);

        verify(wizLightingService).startAlternatingFromPayload(Map.of("lighting_spec", MARS_SPEC));
        verify(wizLightingService, never()).stopActiveJob();
        assertThat(coordinator.isLitBy("session_a")).isTrue();
    }

    @Test
    void stopsInsteadOfRestoringAnOwnerWhoseLightingProgramLongEnded() {
        coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), true);
        // MARS_SPEC 프로그램 10분 + 여유 10분이 지나면 owner 는 버려진 것으로 본다(앱이 stop 없이 종료된 경우).
        clock.advance(Duration.ofMinutes(21));

        coordinator.onGenerated("session_b", generated(VENUS_SPEC, "job_b"), false);

        verify(wizLightingService).stopActiveJob();
        verify(wizLightingService, never()).startAlternatingFromPayload(any());
        assertThat(coordinator.isLitBy("session_a")).isFalse();
    }

    @Test
    void reapplyingRenewsTheOwnerLease() {
        when(wizLightingService.shouldAutoApply()).thenReturn(true);
        coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), true);
        clock.advance(Duration.ofMinutes(15));
        coordinator.turnOn("session_a");
        clock.advance(Duration.ofMinutes(15));

        coordinator.onGenerated("session_b", generated(VENUS_SPEC, "job_b"), false);

        verify(wizLightingService, times(2)).startAlternatingFromPayload(Map.of("lighting_spec", MARS_SPEC));
        verify(wizLightingService, never()).stopActiveJob();
    }

    @Test
    void turnOnReportsUnreachableBulbsAsLightingDisabled() {
        when(wizLightingService.shouldAutoApply()).thenReturn(true);
        coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), false);
        when(wizLightingService.startAlternatingFromPayload(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "WiZ bulb timed out"));

        assertThatThrownBy(() -> coordinator.turnOn("session_a"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.LIGHTING_DISABLED);
    }

    @Test
    void turnOnReportsBrokenRememberedSpecAsInternalError() {
        when(wizLightingService.shouldAutoApply()).thenReturn(true);
        coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), false);
        when(wizLightingService.startAlternatingFromPayload(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "lighting_spec.phases is required"));

        assertThatThrownBy(() -> coordinator.turnOn("session_a"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.INTERNAL);
        assertThat(coordinator.isLitBy("session_a")).isFalse();
    }

    @Test
    void leavesOtherSessionLightingAloneWhenNothingWasAutoApplied() {
        coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), true);

        coordinator.onGenerated("session_b", Map.of("wizLighting", Map.of("enabled", false)), false);

        verify(wizLightingService, never()).stopActiveJob();
        assertThat(coordinator.isLitBy("session_a")).isTrue();
    }

    @Test
    void failedGenerationRestoresSameSessionPreviousSegmentLighting() {
        when(wizLightingService.shouldAutoApply()).thenReturn(true);
        coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), true);

        coordinator.onGenerationFailed(generated(VENUS_SPEC, "job_failed"));

        verify(wizLightingService).startAlternatingFromPayload(Map.of("lighting_spec", MARS_SPEC));
        verify(wizLightingService, never()).stopActiveJob();
        assertThat(coordinator.isLitBy("session_a")).isTrue();
    }

    @Test
    void failedGenerationStopsLightingWhenNobodyHeldIt() {
        coordinator.onGenerationFailed(generated(VENUS_SPEC, "job_failed"));

        verify(wizLightingService).stopActiveJob();
    }

    @Test
    void failedGenerationWithoutAutoApplyLeavesLightingAlone() {
        coordinator.onGenerationFailed(Map.of("wizLighting", Map.of("enabled", false)));

        verify(wizLightingService, never()).stopActiveJob();
        verify(wizLightingService, never()).startAlternatingFromPayload(any());
    }

    @Test
    void turnOnReappliesRememberedSpec() {
        when(wizLightingService.shouldAutoApply()).thenReturn(true);
        coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), false);

        boolean lit = coordinator.turnOn("session_a");

        assertThat(lit).isTrue();
        assertThat(coordinator.isLitBy("session_a")).isTrue();
        verify(wizLightingService).startAlternatingFromPayload(Map.of("lighting_spec", MARS_SPEC));
    }

    @Test
    void turnOnWithoutRememberedSpecWaitsForNextGeneration() {
        when(wizLightingService.shouldAutoApply()).thenReturn(true);

        boolean lit = coordinator.turnOn("session_unknown");

        assertThat(lit).isFalse();
        verify(wizLightingService, never()).startAlternatingFromPayload(any());
    }

    @Test
    void turnOnRejectsWhenWizIsNotConfigured() {
        when(wizLightingService.shouldAutoApply()).thenReturn(false);

        assertThatThrownBy(() -> coordinator.turnOn("session_a"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.LIGHTING_DISABLED);
    }

    @Test
    void turnOffIgnoresSessionThatDoesNotOwnLighting() {
        coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), true);

        coordinator.turnOff("session_b");

        verify(wizLightingService, never()).stopActiveJob();
        assertThat(coordinator.isLitBy("session_a")).isTrue();
    }

    @Test
    void forgetStopsOwnedLightingAndDropsSpec() {
        coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), true);

        coordinator.forget("session_a");

        verify(wizLightingService).stopActiveJob();
        assertThat(coordinator.isLitBy("session_a")).isFalse();
        when(wizLightingService.shouldAutoApply()).thenReturn(true);
        assertThat(coordinator.turnOn("session_a")).isFalse();
    }

    @Test
    void stopAllClearsOwner() {
        coordinator.onGenerated("session_a", generated(MARS_SPEC, "job_a"), true);

        coordinator.stopAll();

        verify(wizLightingService).stopActiveJob();
        assertThat(coordinator.isLitBy("session_a")).isFalse();
    }

    private Map<String, Object> generated(Map<String, Object> lightingSpec, String jobId) {
        return Map.of(
                "interventionResult", Map.of("lighting_spec", lightingSpec),
                "wizLighting", Map.of("active", true, "jobId", jobId)
        );
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
