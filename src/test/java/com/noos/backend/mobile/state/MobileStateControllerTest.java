package com.noos.backend.mobile.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.ai.dto.EegRecognitionRequest;
import com.noos.backend.ai.service.NoosAiService;
import com.noos.backend.mobile.state.service.PlanetRecommender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MobileStateControllerTest {

    private static final String DEVICE_ID = "dev_state_test_001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NoosAiService noosAiService;

    @MockBean
    private PlanetRecommender planetRecommender;

    @BeforeEach
    void setUp() {
        when(planetRecommender.recommend(any()))
                .thenReturn(new PlanetRecommender.Recommendation(
                        "Mars",
                        List.of("Earth", "Neptune"),
                        0.82
                ));
        when(noosAiService.recognizeFromSummary(any(EegRecognitionRequest.class)))
                .thenReturn(Map.of(
                        "currentState", Map.of(
                                "focus_readiness", 0.9,
                                "stress_load", 0.1,
                                "fatigue_risk", 0.2,
                                "relaxation_level", 0.8,
                                "cortical_arousal", 0.7,
                                "mental_workload", 0.2
                        ),
                        "stateLabel", "eeg calm focus"
                ));
    }

    @Test
    void surveyOnlyReturnsCurrentStateAndSurveyWeight() throws Exception {
        mockMvc.perform(post("/api/mobile/state/measure")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "focus", 0.7,
                                "stress", 0.2,
                                "fatigue", 0.3,
                                "relaxation", 0.6,
                                "intentText", "집중"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurementId", startsWith("meas_")))
                .andExpect(jsonPath("$.currentState.focus_readiness").value(0.7))
                .andExpect(jsonPath("$.source").value("survey"))
                .andExpect(jsonPath("$.weight.survey").value(1.0))
                .andExpect(jsonPath("$.weight.eeg").value(0.0))
                .andExpect(jsonPath("$.recommendedPlanet").value("Mars"));
    }

    @Test
    void nullSurveyFieldsDefaultToPointFive() throws Exception {
        mockMvc.perform(post("/api/mobile/state/measure")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("intentText", "쉬고 싶어"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.focus_readiness").value(0.5))
                .andExpect(jsonPath("$.currentState.stress_load").value(0.5))
                .andExpect(jsonPath("$.currentState.fatigue_risk").value(0.5))
                .andExpect(jsonPath("$.currentState.relaxation_level").value(0.5));
    }

    @Test
    void missingSurveyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/mobile/state/measure")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void surveyWithEegReturnsHybridWhenSignalQualityPasses() throws Exception {
        String request = objectMapper.writeValueAsString(Map.of(
                "survey", Map.of(
                        "focus", 0.5,
                        "stress", 0.2,
                        "fatigue", 0.2,
                        "relaxation", 0.6
                ),
                "eeg", Map.of(
                        "deviceType", "Muse S Athena",
                        "deviceId", "muse_test",
                        "measuredAt", "2026-05-22T00:00:00Z",
                        "measurementDurationSec", 60,
                        "sampleRateHz", 256,
                        "sampleCount", 15360,
                        "signalQuality", 0.84,
                        "bands", fullBands()
                )
        ));

        mockMvc.perform(post("/api/mobile/state/measure")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("hybrid"))
                .andExpect(jsonPath("$.stateLabel").value("eeg calm focus"))
                .andExpect(jsonPath("$.weight.eeg").value(0.6))
                .andExpect(jsonPath("$.weight.survey").value(0.4))
                .andExpect(jsonPath("$.currentState.focus_readiness").value(0.74));
    }

    @Test
    void outOfRangeSurveyValuesAreClamped() throws Exception {
        mockMvc.perform(post("/api/mobile/state/measure")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "focus", 2.0,
                                "stress", -1.0,
                                "fatigue", 0.3,
                                "relaxation", 0.4
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.focus_readiness").value(1.0))
                .andExpect(jsonPath("$.currentState.stress_load").value(0.0));
    }

    @Test
    void planetRecommenderReceivesCurrentStatePayload() throws Exception {
        mockMvc.perform(post("/api/mobile/state/measure")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of(
                        "focus", 0.7,
                        "stress", 0.2,
                        "fatigue", 0.4,
                        "relaxation", 0.8
                ))))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Double>> captor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(planetRecommender).recommend(captor.capture());
        Map<String, Double> currentState = captor.getValue();

        assertThat(currentState)
                .containsEntry("focus_readiness", 0.7)
                .containsEntry("stress_load", 0.2)
                .containsEntry("fatigue_risk", 0.4)
                .containsEntry("relaxation_level", 0.8)
                .containsEntry("cortical_arousal", 0.44999999999999996)
                .containsEntry("mental_workload", 0.30000000000000004);
    }

    @Test
    void lowQualityEegFallsBackToSurveyOnly() throws Exception {
        String request = objectMapper.writeValueAsString(Map.of(
                "survey", Map.of(
                        "focus", 0.6,
                        "stress", 0.2,
                        "fatigue", 0.2,
                        "relaxation", 0.7
                ),
                "eeg", Map.of(
                        "deviceType", "Muse S Athena",
                        "signalQuality", 0.2,
                        "bands", fullBands()
                )
        ));

        mockMvc.perform(post("/api/mobile/state/measure")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("survey"))
                .andExpect(jsonPath("$.weight.survey").value(1.0))
                .andExpect(jsonPath("$.weight.eeg").value(0.0));
    }

    @Test
    void surveyOnlyRegressionRemainsSurveySource() throws Exception {
        mockMvc.perform(post("/api/mobile/state/measure")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "focus", 0.6,
                                "stress", 0.2,
                                "fatigue", 0.2,
                                "relaxation", 0.7
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("survey"))
                .andExpect(jsonPath("$.weight.survey").value(1.0))
                .andExpect(jsonPath("$.weight.eeg").value(0.0));
    }

    @Test
    void eegPayloadIsPassedToRecognizeFromSummary() throws Exception {
        String request = objectMapper.writeValueAsString(Map.of(
                "survey", Map.of(
                        "focus", 0.6,
                        "stress", 0.2,
                        "fatigue", 0.2,
                        "relaxation", 0.7
                ),
                "eeg", Map.of(
                        "deviceType", "Muse S Athena",
                        "measuredAt", "2026-05-22T00:00:00Z",
                        "measurementDurationSec", 60,
                        "sampleRateHz", 256,
                        "sampleCount", 15360,
                        "signalQuality", 0.84,
                        "bands", fullBands()
                )
        ));

        mockMvc.perform(post("/api/mobile/state/measure")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());

        ArgumentCaptor<EegRecognitionRequest> captor = ArgumentCaptor.forClass(EegRecognitionRequest.class);
        org.mockito.Mockito.verify(noosAiService).recognizeFromSummary(captor.capture());
        EegRecognitionRequest eegRequest = captor.getValue();

        assertThat(eegRequest.deviceType()).isEqualTo("Muse S Athena");
        assertThat(eegRequest.measuredAt()).isEqualTo("2026-05-22T00:00:00Z");
        assertThat(eegRequest.measurementDurationSec()).isEqualTo(60);
        assertThat(eegRequest.sampleRateHz()).isEqualTo(256);
        assertThat(eegRequest.sampleCount()).isEqualTo(15360);
        assertThat(eegRequest.delta()).isEqualTo(12.3);
        assertThat(eegRequest.theta()).isEqualTo(18.4);
        assertThat(eegRequest.alpha()).isEqualTo(28.2);
        assertThat(eegRequest.beta()).isEqualTo(31.5);
        assertThat(eegRequest.gamma()).isEqualTo(9.6);
    }

    @Test
    void missingEegBandDefaultsToZero() throws Exception {
        String request = objectMapper.writeValueAsString(Map.of(
                "survey", Map.of(
                        "focus", 0.6,
                        "stress", 0.2,
                        "fatigue", 0.2,
                        "relaxation", 0.7
                ),
                "eeg", Map.of(
                        "deviceType", "Muse S Athena",
                        "signalQuality", 0.84,
                        "bands", Map.of(
                                "delta", 12.3,
                                "theta", 18.4,
                                "alpha", 28.2,
                                "beta", 31.5
                        )
                )
        ));

        mockMvc.perform(post("/api/mobile/state/measure")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("hybrid"));

        ArgumentCaptor<EegRecognitionRequest> captor = ArgumentCaptor.forClass(EegRecognitionRequest.class);
        org.mockito.Mockito.verify(noosAiService).recognizeFromSummary(captor.capture());
        assertThat(captor.getValue().gamma()).isEqualTo(0.0);
    }

    private String body(Map<String, Object> survey) throws Exception {
        return objectMapper.writeValueAsString(Map.of("survey", survey));
    }

    private Map<String, Double> fullBands() {
        return Map.of(
                "delta", 12.3,
                "theta", 18.4,
                "alpha", 28.2,
                "beta", 31.5,
                "gamma", 9.6
        );
    }
}
