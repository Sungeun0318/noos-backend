package com.noos.backend.mobile.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.mobile.session.service.GenerationWorker;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "noos.mobile.ratelimit.default-get.limit=2",
        "noos.mobile.ratelimit.default-get.per-minutes=1"
})
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GenerationWorker generationWorker;

    @Test
    void sessionsCreateReturnsTooManyRequestsAfterSixRequestsPerDevice() throws Exception {
        String deviceId = uniqueDeviceId();
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/mobile/sessions")
                            .header("x-device-id", deviceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validSessionBody()))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/mobile/sessions")
                        .header("x-device-id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSessionBody()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSec").isNumber());
    }

    @Test
    void sessionsCreateUsesSeparateBucketPerDevice() throws Exception {
        String saturatedDeviceId = uniqueDeviceId();
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/mobile/sessions")
                            .header("x-device-id", saturatedDeviceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validSessionBody()))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/mobile/sessions")
                        .header("x-device-id", uniqueDeviceId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSessionBody()))
                .andExpect(status().isAccepted());
    }

    @Test
    void defaultGetPolicyCanRateLimitMobileGets() throws Exception {
        String deviceId = uniqueDeviceId();
        mockMvc.perform(get("/api/mobile/sessions").header("x-device-id", deviceId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/mobile/sessions").header("x-device-id", deviceId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/mobile/sessions").header("x-device-id", deviceId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
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
                "measurementId", "meas_rate"
        ));
    }

    private String uniqueDeviceId() {
        return "dev_ratelimit_" + UUID.randomUUID().toString().replace("-", "");
    }
}
