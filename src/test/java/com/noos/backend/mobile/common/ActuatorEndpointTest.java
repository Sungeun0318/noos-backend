package com.noos.backend.mobile.common;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.mobile.session.service.GenerationWorker;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GenerationWorker generationWorker;

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void prometheusExposesMobileMetricsAfterEnqueue() throws Exception {
        mockMvc.perform(post("/api/mobile/sessions")
                        .header("x-device-id", "dev_actuator_metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSessionBody()))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("noos_mobile_session_enqueue_count")));
    }

    private String validSessionBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "planet", "Mars",
                "durationSec", 600,
                "currentState", Map.of(
                        "focus_readiness", 0.5,
                        "stress_load", 0.3,
                        "fatigue_risk", 0.2,
                        "relaxation_level", 0.4,
                        "cortical_arousal", 0.5,
                        "mental_workload", 0.4
                ),
                "stateLabel", "calm focus",
                "intentText", "focus",
                "source", "hybrid",
                "lightingEnabled", false,
                "measurementId", "meas_actuator"
        ));
    }
}
