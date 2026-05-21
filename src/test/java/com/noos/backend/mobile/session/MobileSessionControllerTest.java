package com.noos.backend.mobile.session;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.noos.backend.mobile.session.service.GenerationWorker;

@SpringBootTest
@AutoConfigureMockMvc
class MobileSessionControllerTest {

    private static final String DEVICE_ID = "dev_session_test_001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GenerationWorker generationWorker;

    @Test
    void enqueueReturnsAcceptedQueuedSession() throws Exception {
        mockMvc.perform(post("/api/mobile/sessions")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSessionBody()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sessionId", startsWith("session_")))
                .andExpect(jsonPath("$.status").value("queued"));
    }

    @Test
    void enqueueWithoutPlanetReturnsBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "durationSec", 600,
                "currentState", currentState()
        ));

        mockMvc.perform(post("/api/mobile/sessions")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getIssuedSessionReturnsQueuedWithNullAudio() throws Exception {
        String sessionId = createSession();

        mockMvc.perform(get("/api/mobile/sessions/{id}", sessionId)
                        .header("x-device-id", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.audio").doesNotExist());
    }

    @Test
    void getUnknownSessionReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/mobile/sessions/unknown_id")
                        .header("x-device-id", DEVICE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsCreatedSessions() throws Exception {
        createSession();

        mockMvc.perform(get("/api/mobile/sessions")
                        .header("x-device-id", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty())
                .andExpect(jsonPath("$.hasMore").isBoolean());
    }

    @Test
    void submitFeedbackReturnsOk() throws Exception {
        String sessionId = createSession();
        String body = objectMapper.writeValueAsString(Map.of(
                "musicFit", 0.8,
                "lightingFit", 0.7,
                "focusResult", 0.9,
                "memo", "ok"
        ));

        mockMvc.perform(post("/api/mobile/sessions/{id}/feedback", sessionId)
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void deleteSoftDeletesSession() throws Exception {
        String sessionId = createSession();

        mockMvc.perform(delete("/api/mobile/sessions/{id}", sessionId)
                        .header("x-device-id", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(get("/api/mobile/sessions/{id}", sessionId)
                        .header("x-device-id", DEVICE_ID))
                .andExpect(status().isNotFound());
    }

    private String createSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mobile/sessions")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSessionBody()))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("sessionId").asText();
    }

    private String validSessionBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "planet", "Mars",
                "durationSec", 600,
                "currentState", currentState(),
                "stateLabel", "calm focus",
                "intentText", "집중",
                "source", "hybrid",
                "lightingEnabled", false,
                "measurementId", "meas_test"
        ));
    }

    private Map<String, Double> currentState() {
        return Map.of(
                "focus_readiness", 0.5,
                "stress_load", 0.3,
                "fatigue_risk", 0.2,
                "relaxation_level", 0.4,
                "cortical_arousal", 0.5,
                "mental_workload", 0.4
        );
    }
}
