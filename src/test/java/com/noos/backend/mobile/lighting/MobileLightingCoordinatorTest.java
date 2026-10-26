package com.noos.backend.mobile.lighting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.noos.backend.lighting.service.WizLightingService;
import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import com.noos.backend.mobile.lighting.service.MobileLightingCoordinator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MobileLightingCoordinatorTest {

    private static final Map<String, Object> MARS_SPEC = Map.of(
            "phases", List.of(Map.of("name", "settle", "primary_hex", "#d26a4a", "secondary_hex", "#4c86ff"))
    );
    private static final Map<String, Object> VENUS_SPEC = Map.of(
            "phases", List.of(Map.of("name", "settle", "primary_hex", "#f2c49b", "secondary_hex", "#ffe6c7"))
    );

    @Mock
    private WizLightingService wizLightingService;

    private MobileLightingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new MobileLightingCoordinator(wizLightingService);
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
}
