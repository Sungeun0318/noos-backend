package com.noos.backend.mobile.health;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.noos.backend.lighting.service.WizLightingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

@SpringBootTest(properties = {
        "noos.ai.ace-step.base-url=http://ace-step.test",
        "noos.mobile.health.ace-step.cache-seconds=10"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MobileHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("mobileHealthRestTemplate")
    private RestTemplate restTemplate;

    @MockBean
    private WizLightingService wizLightingService;

    @Test
    void healthWithoutDeviceIdReturnsOk() throws Exception {
        MockRestServiceServer server = aceStepServer(HttpStatus.OK);
        when(wizLightingService.shouldAutoApply()).thenReturn(false);

        mockMvc.perform(get("/api/mobile/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backend").value("ok"))
                .andExpect(jsonPath("$.ai").value("unknown"))
                .andExpect(jsonPath("$.aceStep").value("ok"))
                .andExpect(jsonPath("$.lighting").value("unknown"))
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.minAppVersion").exists())
                .andExpect(jsonPath("$.serverTime").exists());

        server.verify();
        verify(wizLightingService, never()).status();
    }

    @Test
    void healthWithDeviceIdReturnsOk() throws Exception {
        aceStepServer(HttpStatus.OK);
        when(wizLightingService.shouldAutoApply()).thenReturn(false);

        mockMvc.perform(get("/api/mobile/health")
                        .header("x-device-id", "dev_test_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backend").value("ok"))
                .andExpect(jsonPath("$.ai").value("unknown"))
                .andExpect(jsonPath("$.aceStep").value("ok"))
                .andExpect(jsonPath("$.lighting").value("unknown"))
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.minAppVersion").exists())
                .andExpect(jsonPath("$.serverTime").exists());
    }

    @Test
    void aceStepHealthIsCachedWithinTtl() throws Exception {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://ace-step.test/health"))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.OK));
        when(wizLightingService.shouldAutoApply()).thenReturn(false);

        mockMvc.perform(get("/api/mobile/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceStep").value("ok"));
        mockMvc.perform(get("/api/mobile/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceStep").value("ok"));

        server.verify();
    }

    @Test
    void aceStepServerErrorReturnsDown() throws Exception {
        aceStepServer(HttpStatus.INTERNAL_SERVER_ERROR);
        when(wizLightingService.shouldAutoApply()).thenReturn(false);

        mockMvc.perform(get("/api/mobile/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceStep").value("down"));
    }

    @Test
    void aceStepNetworkFailureReturnsDegraded() throws Exception {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://ace-step.test/health"))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("timeout");
                });
        when(wizLightingService.shouldAutoApply()).thenReturn(false);

        mockMvc.perform(get("/api/mobile/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceStep").value("degraded"));

        server.verify();
    }

    @Test
    void mobileEndpointWithoutDeviceIdReturnsMissingDeviceId() throws Exception {
        mockMvc.perform(get("/api/mobile/sessions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_DEVICE_ID"));
    }

    private MockRestServiceServer aceStepServer(HttpStatus status) {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://ace-step.test/health"))
                .andRespond(MockRestResponseCreators.withStatus(status));
        return server;
    }
}
