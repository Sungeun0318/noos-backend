package com.noos.backend.mobile.adaptive.controller;

import com.noos.backend.mobile.adaptive.dto.AdaptiveFeedbackRequest;
import com.noos.backend.mobile.adaptive.dto.AdaptiveFeedbackResponse;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionResponse;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionStartRequest;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionStartResponse;
import com.noos.backend.mobile.adaptive.dto.AdaptiveSessionStatusResponse;
import com.noos.backend.mobile.adaptive.dto.AdaptiveWindowSubmitRequest;
import com.noos.backend.mobile.adaptive.dto.AdaptiveWindowSubmitResponse;
import com.noos.backend.mobile.adaptive.dto.PauseAdaptiveSessionRequest;
import com.noos.backend.mobile.adaptive.service.AdaptiveSessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/adaptive/sessions")
public class AdaptiveSessionController {

    private final AdaptiveSessionService adaptiveSessionService;

    public AdaptiveSessionController(AdaptiveSessionService adaptiveSessionService) {
        this.adaptiveSessionService = adaptiveSessionService;
    }

    @PostMapping("/start")
    public AdaptiveSessionStartResponse start(@RequestHeader("x-device-id") String deviceId,
                                              @RequestBody(required = false) AdaptiveSessionStartRequest request) {
        return adaptiveSessionService.start(request, deviceId);
    }

    @GetMapping("/{sessionId}")
    public AdaptiveSessionResponse get(@RequestHeader("x-device-id") String deviceId,
                                       @PathVariable String sessionId) {
        return adaptiveSessionService.get(sessionId, deviceId);
    }

    @PostMapping("/{sessionId}/windows")
    public AdaptiveWindowSubmitResponse submitWindow(@RequestHeader("x-device-id") String deviceId,
                                                     @PathVariable String sessionId,
                                                     @RequestBody AdaptiveWindowSubmitRequest request) {
        return adaptiveSessionService.submitWindow(sessionId, deviceId, request);
    }

    @PostMapping("/{sessionId}/pause")
    public AdaptiveSessionStatusResponse pause(@RequestHeader("x-device-id") String deviceId,
                                               @PathVariable String sessionId,
                                               @RequestBody(required = false) PauseAdaptiveSessionRequest request) {
        return adaptiveSessionService.pause(sessionId, deviceId, request);
    }

    @PostMapping("/{sessionId}/resume")
    public AdaptiveSessionStatusResponse resume(@RequestHeader("x-device-id") String deviceId,
                                                @PathVariable String sessionId) {
        return adaptiveSessionService.resume(sessionId, deviceId);
    }

    @PostMapping("/{sessionId}/end")
    public AdaptiveSessionStatusResponse end(@RequestHeader("x-device-id") String deviceId,
                                             @PathVariable String sessionId) {
        return adaptiveSessionService.end(sessionId, deviceId);
    }

    @PostMapping("/{sessionId}/feedback")
    public AdaptiveFeedbackResponse submitFeedback(@RequestHeader("x-device-id") String deviceId,
                                                   @PathVariable String sessionId,
                                                   @RequestBody(required = false) AdaptiveFeedbackRequest request) {
        return adaptiveSessionService.submitFeedback(sessionId, deviceId, request);
    }
}
