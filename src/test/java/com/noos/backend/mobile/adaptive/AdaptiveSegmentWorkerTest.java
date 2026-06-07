package com.noos.backend.mobile.adaptive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.noos.backend.ai.dto.InterventionGenerationRequest;
import com.noos.backend.ai.service.NoosAiService;
import com.noos.backend.mobile.adaptive.mapper.SessionSegmentMapper;
import com.noos.backend.mobile.adaptive.service.AdaptiveSegmentContext;
import com.noos.backend.mobile.adaptive.service.AdaptiveSegmentWorker;
import com.noos.backend.mobile.audio.service.AudioRegistryService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdaptiveSegmentWorkerTest {

    @Mock
    private SessionSegmentMapper sessionSegmentMapper;

    @Mock
    private NoosAiService noosAiService;

    @Mock
    private AudioRegistryService audioRegistry;

    private AdaptiveSegmentWorker worker;

    @BeforeEach
    void setUp() {
        worker = new AdaptiveSegmentWorker(sessionSegmentMapper, noosAiService, audioRegistry);
    }

    @Test
    void runMarksGeneratingThenReadyWithRegisteredAudio() {
        when(noosAiService.generateIntervention(any(InterventionGenerationRequest.class))).thenReturn(Map.of(
                "audioUrl", "/api/ai/audio?path=%2Ftmp%2Fnoos%2Fadaptive.mp3"
        ));
        when(audioRegistry.register("/tmp/noos/adaptive.mp3", "session_adaptive", "audio/mpeg", 120))
                .thenReturn("audio_adaptive");

        worker.run(101L, context());

        ArgumentCaptor<InterventionGenerationRequest> requestCaptor = ArgumentCaptor.forClass(InterventionGenerationRequest.class);
        verify(noosAiService).generateIntervention(requestCaptor.capture());
        assertThat(requestCaptor.getValue().planet()).isEqualTo("Mars");
        assertThat(requestCaptor.getValue().durationSec()).isEqualTo(120);
        assertThat(requestCaptor.getValue().memoText()).isNull();
        assertThat(requestCaptor.getValue().currentState()).containsEntry("focus_readiness", 0.5);

        verify(sessionSegmentMapper).updateStatus(eq(101L), eq("generating"), isNull(), any(Instant.class), isNull(), isNull(), isNull());
        verify(audioRegistry).register("/tmp/noos/adaptive.mp3", "session_adaptive", "audio/mpeg", 120);
        verify(sessionSegmentMapper).updateStatus(eq(101L), eq("ready"), eq("audio_adaptive"), any(Instant.class), any(Instant.class), isNull(), isNull());
    }

    @Test
    void runMarksFailedWithAceStepErrorCodeForFiveHundredFailure() {
        when(noosAiService.generateIntervention(any(InterventionGenerationRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ace down"));

        worker.run(101L, context());

        verify(sessionSegmentMapper).updateStatus(eq(101L), eq("generating"), isNull(), any(Instant.class), isNull(), isNull(), isNull());
        verify(sessionSegmentMapper).updateStatus(101L, "failed", null, null, null, null, "ACE_STEP_DOWN");
    }

    @Test
    void runMarksFailedWithGenericCodeWhenAudioPathMissing() {
        when(noosAiService.generateIntervention(any(InterventionGenerationRequest.class))).thenReturn(Map.of());

        worker.run(101L, context());

        verify(sessionSegmentMapper).updateStatus(eq(101L), eq("generating"), isNull(), any(Instant.class), isNull(), isNull(), isNull());
        verify(sessionSegmentMapper).updateStatus(101L, "failed", null, null, null, null, "GENERATION_FAILED");
    }

    private AdaptiveSegmentContext context() {
        return new AdaptiveSegmentContext(
                "session_adaptive",
                "Mars",
                120,
                Map.of(
                        "focus_readiness", 0.5,
                        "stress_load", 0.3,
                        "fatigue_risk", 0.2,
                        "relaxation_level", 0.6,
                        "cortical_arousal", 0.45,
                        "mental_workload", 0.25
                )
        );
    }
}
