package com.noos.backend.mobile.lighting;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.noos.backend.lighting.service.WizLightingService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MobileLightingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WizLightingService wizLightingService;

    @Test
    void stopReturnsWizLightingServiceResult() throws Exception {
        when(wizLightingService.stopActiveJob()).thenReturn(Map.of(
                "active", false,
                "stoppedAt", "2026-05-26T00:00:00Z"
        ));

        mockMvc.perform(post("/api/mobile/lighting/stop")
                        .header("x-device-id", "dev_lighting_test_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.stoppedAt").value("2026-05-26T00:00:00Z"));

        verify(wizLightingService, times(1)).stopActiveJob();
    }

    @Test
    void statusReturnsWizLightingServiceResult() throws Exception {
        when(wizLightingService.status()).thenReturn(Map.of(
                "active", true,
                "enabled", true
        ));

        mockMvc.perform(get("/api/mobile/lighting/status")
                        .header("x-device-id", "dev_lighting_test_002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(wizLightingService, times(1)).status();
    }
}
