package com.noos.backend.mobile.adaptive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.mobile.adaptive.service.AdaptiveSegmentWorker;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdaptiveSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private AdaptiveSegmentWorker adaptiveSegmentWorker;

    @Test
    void startStoresLightingFlagAndGetReturnsIt() throws Exception {
        String deviceId = uniqueDeviceId();
        String sessionId = start(deviceId, "{\"seedSource\":\"survey\",\"planetHint\":\"Neptune\",\"lightingEnabled\":true}");

        assertThat(lightingEnabled(sessionId)).isTrue();
        mockMvc.perform(get("/api/mobile/adaptive/sessions/{id}", sessionId)
                        .header("x-device-id", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lighting.enabled").value(true))
                .andExpect(jsonPath("$.lighting.active").value(false));
    }

    @Test
    void startWithoutLightingFlagKeepsLightingOff() throws Exception {
        String sessionId = start(uniqueDeviceId(), "{\"seedSource\":\"survey\",\"planetHint\":\"Neptune\"}");

        assertThat(lightingEnabled(sessionId)).isFalse();
    }

    @Test
    void lightingOffPersistsFlag() throws Exception {
        String deviceId = uniqueDeviceId();
        String sessionId = start(deviceId, "{\"seedSource\":\"survey\",\"lightingEnabled\":true}");

        mockMvc.perform(post("/api/mobile/adaptive/sessions/{id}/lighting", sessionId)
                        .header("x-device-id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.active").value(false));

        assertThat(lightingEnabled(sessionId)).isFalse();
    }

    @Test
    void lightingOnWithoutWizConfigReturnsServiceUnavailable() throws Exception {
        String deviceId = uniqueDeviceId();
        String sessionId = start(deviceId, "{\"seedSource\":\"survey\"}");

        mockMvc.perform(post("/api/mobile/adaptive/sessions/{id}/lighting", sessionId)
                        .header("x-device-id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("LIGHTING_DISABLED"));

        assertThat(lightingEnabled(sessionId)).isFalse();
    }

    @Test
    void lightingToggleAfterEndReturnsConflict() throws Exception {
        String deviceId = uniqueDeviceId();
        String sessionId = start(deviceId, "{\"seedSource\":\"survey\",\"lightingEnabled\":true}");
        mockMvc.perform(post("/api/mobile/adaptive/sessions/{id}/end", sessionId)
                        .header("x-device-id", deviceId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mobile/adaptive/sessions/{id}/lighting", sessionId)
                        .header("x-device-id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADAPTIVE_SESSION_STATE_CONFLICT"));
    }

    private String start(String deviceId, String body) throws Exception {
        String response = mockMvc.perform(post("/api/mobile/adaptive/sessions/start")
                        .header("x-device-id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("sessionId").asText();
    }

    private boolean lightingEnabled(String sessionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT lighting_enabled FROM adaptive_session WHERE id = ?",
                Boolean.class,
                sessionId
        ));
    }

    private String uniqueDeviceId() {
        return "dev_adaptive_light_" + UUID.randomUUID().toString().replace("-", "");
    }
}
