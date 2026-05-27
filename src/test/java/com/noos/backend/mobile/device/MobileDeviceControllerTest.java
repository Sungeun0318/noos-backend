package com.noos.backend.mobile.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.mobile.device.service.NotificationService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MobileDeviceControllerTest {

    private static final String DEVICE_ID = "dev_push_test_001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM push_devices WHERE device_id = ?", DEVICE_ID);
    }

    @Test
    void registerPushTokenReturnsDeviceRegistrationId() throws Exception {
        mockMvc.perform(post("/api/mobile/devices/push-token")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ios", "apns", "token_apns_001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.deviceRegistrationId", startsWith("dreg_")));
    }

    @Test
    void registerSameDeviceAndProviderUpsertsSingleRowAndUpdatesToken() throws Exception {
        mockMvc.perform(post("/api/mobile/devices/push-token")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("android", "fcm", "token_old")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mobile/devices/push-token")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("android", "fcm", "token_new")))
                .andExpect(status().isOk());

        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM push_devices WHERE device_id = ? AND provider = ?",
                Integer.class,
                DEVICE_ID,
                "fcm"
        );
        String token = jdbc.queryForObject(
                "SELECT token FROM push_devices WHERE device_id = ? AND provider = ?",
                String.class,
                DEVICE_ID,
                "fcm"
        );

        assertThat(rowCount).isEqualTo(1);
        assertThat(token).isEqualTo("token_new");
    }

    @Test
    void registerWithoutDeviceIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/mobile/devices/push-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ios", "apns", "token_apns_001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_DEVICE_ID"));
    }

    @Test
    void unregisterDeactivatesDeviceRows() throws Exception {
        mockMvc.perform(post("/api/mobile/devices/push-token")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("android", "fcm", "token_fcm_001")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/mobile/devices/push-token")
                        .header("x-device-id", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        Integer active = jdbc.queryForObject(
                "SELECT active FROM push_devices WHERE device_id = ? AND provider = ?",
                Integer.class,
                DEVICE_ID,
                "fcm"
        );

        assertThat(active).isZero();
    }

    @Test
    void registerWithoutPlatformReturnsBadRequest() throws Exception {
        String request = objectMapper.writeValueAsString(Map.of(
                "provider", "fcm",
                "token", "token_fcm_001"
        ));

        mockMvc.perform(post("/api/mobile/devices/push-token")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void notificationServiceNoOpsWithoutFcmCredentials() {
        assertThatCode(() -> notificationService.notifySessionReady(DEVICE_ID, null, "session_test", "Mars"))
                .doesNotThrowAnyException();
    }

    private String body(String platform, String provider, String token) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "platform", platform,
                "provider", provider,
                "token", token,
                "appVersion", "1.0.0",
                "locale", "ko-KR"
        ));
    }
}
