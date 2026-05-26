package com.noos.backend.mobile.session.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.mobile.common.IdempotencyService;
import com.noos.backend.mobile.session.dto.EnqueueRequest;
import com.noos.backend.mobile.session.dto.EnqueueResponse;
import com.noos.backend.mobile.session.dto.FeedbackRequest;
import com.noos.backend.mobile.session.dto.FeedbackResponse;
import com.noos.backend.mobile.session.dto.SessionListResponse;
import com.noos.backend.mobile.session.dto.SessionResponse;
import com.noos.backend.mobile.session.service.MobileSessionService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/sessions")
public class MobileSessionController {

    private static final String SESSIONS_ENQUEUE_SCOPE = "sessions.enqueue";
    private static final String SESSIONS_FEEDBACK_SCOPE = "sessions.feedback";

    private final MobileSessionService sessionService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public MobileSessionController(MobileSessionService sessionService,
                                   IdempotencyService idempotencyService,
                                   ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> enqueue(@RequestHeader("x-device-id") String deviceId,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                     @Valid @RequestBody EnqueueRequest request) {
        return idempotent(idempotencyKey, SESSIONS_ENQUEUE_SCOPE, deviceId, HttpStatus.ACCEPTED,
                () -> sessionService.enqueue(request, deviceId));
    }

    @GetMapping("/{sessionId}")
    public SessionResponse get(@RequestHeader("x-device-id") String deviceId,
                               @PathVariable String sessionId) {
        return sessionService.get(sessionId, deviceId);
    }

    @GetMapping
    public SessionListResponse list(@RequestHeader("x-device-id") String deviceId,
                                    @RequestParam(required = false) String cursor,
                                    @RequestParam(defaultValue = "20") int limit,
                                    @RequestParam(required = false) String status) {
        return sessionService.list(deviceId, cursor, limit, parseStatus(status));
    }

    @DeleteMapping("/{sessionId}")
    public Map<String, Boolean> delete(@RequestHeader("x-device-id") String deviceId,
                                       @PathVariable String sessionId) {
        sessionService.delete(sessionId, deviceId);
        return Map.of("ok", true);
    }

    @PostMapping("/{sessionId}/feedback")
    public ResponseEntity<?> submitFeedback(@RequestHeader("x-device-id") String deviceId,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                            @PathVariable String sessionId,
                                            @RequestBody FeedbackRequest request) {
        return idempotent(idempotencyKey, SESSIONS_FEEDBACK_SCOPE, deviceId, HttpStatus.OK,
                () -> sessionService.submitFeedback(sessionId, deviceId, request));
    }

    private List<String> parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return List.of();
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private ResponseEntity<?> idempotent(String idempotencyKey,
                                         String scope,
                                         String deviceId,
                                         HttpStatus freshStatus,
                                         ResponseSupplier supplier) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyService.tryCachedResponse(idempotencyKey, scope, deviceId);
            if (cached.isPresent()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(cached.get());
            }
        }

        Object response = supplier.get();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.storeResponse(idempotencyKey, scope, deviceId, writeJson(response));
        }
        return ResponseEntity.status(freshStatus).body(response);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_RESPONSE_SERIALIZATION_FAILED", e);
        }
    }

    @FunctionalInterface
    private interface ResponseSupplier {
        Object get();
    }
}
