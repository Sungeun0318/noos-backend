package com.noos.backend.ai.controller;

import com.noos.backend.ai.dto.EegRecognitionRequest;
import com.noos.backend.ai.dto.InterventionGenerationRequest;
import com.noos.backend.ai.dto.PlanetRecommendationRequest;
import com.noos.backend.ai.service.NoosAiService;
import com.noos.backend.auth.service.AuthSessionService;
import com.noos.backend.auth.session.SessionUser;
import com.noos.backend.eeg.dto.EegRawChunkUploadRequest;
import com.noos.backend.eeg.dto.EegRawChunkUploadResponse;
import com.noos.backend.eeg.dto.EegSessionStartRequest;
import com.noos.backend.eeg.dto.EegSessionStartResponse;
import com.noos.backend.eeg.service.EegAnalysisService;
import com.noos.backend.eeg.service.EegRawChunkService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class NoosAiController {

    private static final Logger logger = LoggerFactory.getLogger(NoosAiController.class);

    private final NoosAiService noosAiService;
    private final EegAnalysisService eegAnalysisService;
    private final EegRawChunkService eegRawChunkService;
    private final AuthSessionService authSessionService;

    public NoosAiController(
            NoosAiService noosAiService,
            EegAnalysisService eegAnalysisService,
            EegRawChunkService eegRawChunkService,
            AuthSessionService authSessionService
    ) {
        this.noosAiService = noosAiService;
        this.eegAnalysisService = eegAnalysisService;
        this.eegRawChunkService = eegRawChunkService;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/eeg/sessions/start")
    public EegSessionStartResponse startEegSession(
            @RequestBody EegSessionStartRequest request,
            HttpServletRequest httpServletRequest
    ) {
        Long sessionUserId = resolveSessionUserId(httpServletRequest);
        return eegAnalysisService.startSession(request, sessionUserId);
    }

    @PostMapping("/eeg/raw/chunks")
    public EegRawChunkUploadResponse uploadEegRawChunk(
            @RequestBody EegRawChunkUploadRequest request,
            HttpServletRequest httpServletRequest
    ) {
        Long sessionUserId = resolveSessionUserId(httpServletRequest);
        if (sessionUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login session is required for EEG raw upload.");
        }

        logger.info(
                "Received EEG raw chunk: sessionUserId={}, eegSessionId={}, chunkIndex={}, sampleCount={}",
                sessionUserId,
                request.eegSessionId(),
                request.chunkIndex(),
                request.samples() != null ? request.samples().size() : 0
        );
        return eegRawChunkService.saveChunk(request);
    }

    @PostMapping("/eeg/results")
    public Map<String, Object> analyzeEeg(
            @RequestBody EegRecognitionRequest request,
            HttpServletRequest httpServletRequest
    ) {
        Long sessionUserId = resolveSessionUserId(httpServletRequest);

        logger.info(
                "Received EEG summary from frontend: sessionUserId={}, eegSessionId={}, measuredAt={}, durationSec={}, sampleCount={}, dominantBand={}",
                sessionUserId,
                request.eegSessionId(),
                request.measuredAt(),
                request.measurementDurationSec(),
                request.sampleCount(),
                request.dominantBand()
        );
        return eegAnalysisService.analyzeAndPersist(request, sessionUserId);
    }

    @PostMapping("/ai/intervention/music")
    public Map<String, Object> generateIntervention(@RequestBody InterventionGenerationRequest request) {
        return noosAiService.generateIntervention(request);
    }

    @PostMapping("/ai/intervention/prewarm")
    public Map<String, Object> prewarmIntervention() {
        return noosAiService.prewarmIntervention();
    }

    @PostMapping("/ai/planet/recommend")
    public Map<String, Object> recommendPlanet(@RequestBody PlanetRecommendationRequest request) {
        return noosAiService.recommendPlanet(request);
    }

    @GetMapping("/ai/audio")
    public ResponseEntity<Resource> streamAudio(@RequestParam("path") String path) {
        return noosAiService.streamGeneratedAudio(path);
    }

    private Long resolveSessionUserId(HttpServletRequest httpServletRequest) {
        SessionUser sessionUser = authSessionService.getSessionUser(httpServletRequest);
        return sessionUser != null ? sessionUser.userId() : null;
    }
}
