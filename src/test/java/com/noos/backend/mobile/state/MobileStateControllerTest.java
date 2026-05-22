package com.noos.backend.mobile.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.ai.dto.PlanetRecommendationRequest;
import com.noos.backend.ai.service.NoosAiService;
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

    @BeforeEach
    void setUp() {
        when(noosAiService.recommendPlanet(any(PlanetRecommendationRequest.class)))
                .thenReturn(Map.of(
                        "recommendedPlanet", "Mars",
                        "alternates", List.of("Earth", "Neptune"),
                        "confidence", 0.82
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
                .andExpect(status().isBadRequest());
    }

    @Test
    void eegPayloadIsAcceptedButIgnoredInBe06() throws Exception {
        String request = objectMapper.writeValueAsString(Map.of(
                "survey", Map.of(
                        "focus", 0.6,
                        "stress", 0.2,
                        "fatigue", 0.2,
                        "relaxation", 0.7
                ),
                "eeg", Map.of(
                        "deviceType", "Muse S Athena",
                        "deviceId", "muse_test",
                        "signalQuality", 0.9,
                        "bands", Map.of("alpha", 1.0)
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
    void recommendPlanetReceivesCurrentStatePayload() throws Exception {
        mockMvc.perform(post("/api/mobile/state/measure")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "focus", 0.7,
                                "stress", 0.2,
                                "fatigue", 0.4,
                                "relaxation", 0.8,
                                "intentText", "집중"
                        ))))
                .andExpect(status().isOk());

        ArgumentCaptor<PlanetRecommendationRequest> captor = ArgumentCaptor.forClass(PlanetRecommendationRequest.class);
        org.mockito.Mockito.verify(noosAiService).recommendPlanet(captor.capture());
        PlanetRecommendationRequest request = captor.getValue();

        assertThat(request.intentText()).isEqualTo("집중");
        assertThat(request.currentState())
                .containsEntry("focus_readiness", 0.7)
                .containsEntry("stress_load", 0.2)
                .containsEntry("fatigue_risk", 0.4)
                .containsEntry("relaxation_level", 0.8)
                .containsEntry("cortical_arousal", 0.44999999999999996)
                .containsEntry("mental_workload", 0.30000000000000004);
    }

    private String body(Map<String, Object> survey) throws Exception {
        return objectMapper.writeValueAsString(Map.of("survey", survey));
    }
}
