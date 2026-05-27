package com.noos.backend.mobile.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiErrorAdviceTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ApiErrorAdvice())
                .setValidator(validator)
                .build();
    }

    @Test
    void apiExceptionReturnsEnvelope() throws Exception {
        mockMvc.perform(get("/api/mobile/test/login-taken"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LOGIN_ID_TAKEN"))
                .andExpect(jsonPath("$.error.message").value("LOGIN_ID_TAKEN"));
    }

    @Test
    void validationFailureReturnsEnvelope() throws Exception {
        mockMvc.perform(post("/api/mobile/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void missingDeviceIdHeaderReturnsEnvelope() throws Exception {
        mockMvc.perform(get("/api/mobile/test/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_DEVICE_ID"));
    }

    @Test
    void unexpectedExceptionReturnsGenericInternalEnvelope() throws Exception {
        mockMvc.perform(get("/api/mobile/test/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL"))
                .andExpect(jsonPath("$.error.message").value("internal server error"));
    }

    @RestController
    private static class TestController {

        @GetMapping("/api/mobile/test/login-taken")
        void loginTaken() {
            throw new ApiException(ErrorCode.LOGIN_ID_TAKEN);
        }

        @PostMapping("/api/mobile/test/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/api/mobile/test/header")
        void header(@RequestHeader("x-device-id") String deviceId) {
        }

        @GetMapping("/api/mobile/test/internal")
        void internal() {
            throw new IllegalStateException("secret implementation detail");
        }
    }

    private record TestRequest(@NotBlank String name) {
    }
}
