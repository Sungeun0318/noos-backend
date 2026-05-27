package com.noos.backend.mobile.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "noos.mobile.min-app-version=9.9.9")
@AutoConfigureMockMvc
class RequestIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatesRequestHeadersWhenRequestIdIsMissing() throws Exception {
        mockMvc.perform(get("/api/mobile/sessions"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("x-request-id", matchesPattern("[0-9a-fA-F-]{36}")))
                .andExpect(header().exists("x-server-time"))
                .andExpect(header().string("x-min-app-version", "9.9.9"));
    }

    @Test
    void echoesRequestIdHeader() throws Exception {
        mockMvc.perform(get("/api/mobile/sessions")
                        .header("x-request-id", "req-test-001"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("x-request-id", "req-test-001"));
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        mockMvc.perform(get("/api/mobile/sessions")
                        .header("x-request-id", "req-clear-001"))
                .andExpect(status().isBadRequest());

        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("deviceId")).isNull();
    }
}
