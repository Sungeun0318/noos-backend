package com.noos.backend.mobile.session.controller;

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
import org.springframework.http.ResponseEntity;
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

    private final MobileSessionService sessionService;

    public MobileSessionController(MobileSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<EnqueueResponse> enqueue(@RequestHeader("x-device-id") String deviceId,
                                                   @Valid @RequestBody EnqueueRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(sessionService.enqueue(request, deviceId));
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
    public FeedbackResponse submitFeedback(@RequestHeader("x-device-id") String deviceId,
                                           @PathVariable String sessionId,
                                           @RequestBody FeedbackRequest request) {
        return sessionService.submitFeedback(sessionId, deviceId, request);
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
}
