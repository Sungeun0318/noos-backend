package com.noos.backend.mobile.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.noos.backend.mobile.session.service.GenerationWorker;

@SpringBootTest(properties = "noos.mobile.ratelimit.sessions-create.limit=100")
@AutoConfigureMockMvc
class MobileSessionControllerTest {

    private static final String DEVICE_ID = "dev_session_test_001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
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
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
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
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void enqueueWithSameIdempotencyKeyReturnsCachedResponse() throws Exception {
        String deviceId = uniqueDeviceId();
        String key = "idem-" + UUID.randomUUID();

        MvcResult first = mockMvc.perform(post("/api/mobile/sessions")
                        .header("x-device-id", deviceId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSessionBody()))
                .andExpect(status().isAccepted())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/mobile/sessions")
                        .header("x-device-id", deviceId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSessionBody()))
                .andExpect(status().isOk())
                .andReturn();

        String firstSessionId = sessionId(first);
        String secondSessionId = sessionId(second);
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mobile_sessions WHERE device_id = ?",
                Integer.class,
                deviceId
        );

        assertThat(secondSessionId).isEqualTo(firstSessionId);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void enqueueWithoutIdempotencyKeyCreatesSeparateSessions() throws Exception {
        String deviceId = uniqueDeviceId();

        String firstSessionId = sessionId(mockMvc.perform(post("/api/mobile/sessions")
                        .header("x-device-id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSessionBody()))
                .andExpect(status().isAccepted())
                .andReturn());
        String secondSessionId = sessionId(mockMvc.perform(post("/api/mobile/sessions")
                        .header("x-device-id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSessionBody()))
                .andExpect(status().isAccepted())
                .andReturn());

        assertThat(secondSessionId).isNotEqualTo(firstSessionId);
    }

    @Test
    void enqueueWithSameIdempotencyKeyFromDifferentDeviceReturnsConflict() throws Exception {
        String key = "idem-" + UUID.randomUUID();

        mockMvc.perform(post("/api/mobile/sessions")
                        .header("x-device-id", uniqueDeviceId())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSessionBody()))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/mobile/sessions")
                        .header("x-device-id", uniqueDeviceId())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSessionBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_CONFLICT"));
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

    private String sessionId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("sessionId").asText();
    }

    private String uniqueDeviceId() {
        return "dev_session_idem_" + UUID.randomUUID().toString().replace("-", "");
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
