package com.noos.backend.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.ai.dto.InterventionGenerationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoosAiServiceTest {

    private final NoosAiService service = new NoosAiService(
            new ObjectMapper(),
            "python3",
            "http://127.0.0.1:8011",
            900,
            true,
            "acestep-v15-turbo",
            false,
            "",
            0,
            0,
            false,
            "http://127.0.0.1:8091",
            120,
            null
    );

    @Test
    void generateInterventionRejectsDurationAboveLimitBeforeRunningCli() {
        InterventionGenerationRequest request = new InterventionGenerationRequest(
                "Neptune",
                Map.of(
                        "focus_readiness", 0.5,
                        "stress_load", 0.5,
                        "fatigue_risk", 0.5,
                        "relaxation_level", 0.5,
                        "cortical_arousal", 0.5,
                        "mental_workload", 0.5
                ),
                Map.of(),
                601,
                1,
                java.util.List.of(),
                "",
                Map.of()
        );

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.generateIntervention(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
}
