package com.noos.backend.mobile.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.mobile.audio.service.AudioUrlSigner;
import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import com.noos.backend.mobile.lighting.dto.SessionLighting;
import com.noos.backend.mobile.lighting.service.MobileLightingCoordinator;
import com.noos.backend.mobile.session.dto.MobileSessionRow;
import com.noos.backend.mobile.session.mapper.MobileSessionMapper;
import com.noos.backend.mobile.session.mapper.SessionFeedbackMapper;
import com.noos.backend.mobile.session.service.GenerationWorker;
import com.noos.backend.mobile.session.service.MobileSessionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MobileSessionServiceLightingTest {

    private static final String DEVICE_ID = "dev_session_lighting_unit";
    private static final String SESSION_ID = "session_lighting_unit";

    @Mock
    private MobileSessionMapper sessionMapper;

    @Mock
    private SessionFeedbackMapper feedbackMapper;

    @Mock
    private GenerationWorker worker;

    @Mock
    private AudioUrlSigner audioUrlSigner;

    @Mock
    private MobileLightingCoordinator lightingCoordinator;

    private MobileSessionService service;

    @BeforeEach
    void setUp() {
        service = new MobileSessionService(
                sessionMapper,
                feedbackMapper,
                new ObjectMapper(),
                worker,
                new SimpleMeterRegistry(),
                audioUrlSigner,
                lightingCoordinator
        );
    }

    @Test
    void lightingOnPersistsFlagAndReappliesRememberedLighting() {
        when(sessionMapper.findById(SESSION_ID)).thenReturn(session(false));
        when(lightingCoordinator.isAvailable()).thenReturn(true);
        when(lightingCoordinator.turnOn(SESSION_ID)).thenReturn(true);

        SessionLighting lighting = service.setLighting(SESSION_ID, DEVICE_ID, true);

        assertThat(lighting).isEqualTo(new SessionLighting(true, true));
        verify(sessionMapper).updateLightingEnabled(SESSION_ID, true);
        verify(sessionMapper, never()).updateLightingEnabled(SESSION_ID, false);
    }

    @Test
    void lightingOnRollsBackFlagWhenTurnOnFails() {
        when(sessionMapper.findById(SESSION_ID)).thenReturn(session(false));
        when(lightingCoordinator.isAvailable()).thenReturn(true);
        when(lightingCoordinator.turnOn(SESSION_ID)).thenThrow(new ApiException(ErrorCode.LIGHTING_DISABLED));

        assertThatThrownBy(() -> service.setLighting(SESSION_ID, DEVICE_ID, true))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code)
                .isEqualTo(ErrorCode.LIGHTING_DISABLED);

        InOrder inOrder = inOrder(sessionMapper);
        inOrder.verify(sessionMapper).updateLightingEnabled(SESSION_ID, true);
        inOrder.verify(sessionMapper).updateLightingEnabled(SESSION_ID, false);
    }

    private MobileSessionRow session(boolean lightingEnabled) {
        MobileSessionRow row = new MobileSessionRow();
        row.setId(SESSION_ID);
        row.setDeviceId(DEVICE_ID);
        row.setPlanet("Mars");
        row.setDurationSec(60);
        row.setLightingEnabled(lightingEnabled);
        row.setStatus("ready");
        return row;
    }
}
