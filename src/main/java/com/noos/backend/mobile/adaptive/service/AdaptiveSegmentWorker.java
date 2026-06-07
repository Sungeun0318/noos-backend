package com.noos.backend.mobile.adaptive.service;

import com.noos.backend.ai.dto.InterventionGenerationRequest;
import com.noos.backend.ai.service.NoosAiService;
import com.noos.backend.mobile.adaptive.mapper.SessionSegmentMapper;
import com.noos.backend.mobile.audio.service.AudioRegistryService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AdaptiveSegmentWorker {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveSegmentWorker.class);

    private final SessionSegmentMapper sessionSegmentMapper;
    private final NoosAiService noosAiService;
    private final AudioRegistryService audioRegistry;

    public AdaptiveSegmentWorker(SessionSegmentMapper sessionSegmentMapper,
                                 NoosAiService noosAiService,
                                 AudioRegistryService audioRegistry) {
        this.sessionSegmentMapper = sessionSegmentMapper;
        this.noosAiService = noosAiService;
        this.audioRegistry = audioRegistry;
    }

    @Async("generationExecutor")
    public void run(Long segmentId, AdaptiveSegmentContext ctx) {
        Instant genStartedAt = Instant.now();
        try {
            sessionSegmentMapper.updateStatus(segmentId, "generating", null, genStartedAt, null, null, null);

            Map<String, Object> currentState = new LinkedHashMap<>();
            if (ctx.sixAxisMap() != null) {
                currentState.putAll(ctx.sixAxisMap());
            }

            InterventionGenerationRequest request = new InterventionGenerationRequest(
                    ctx.planet(),
                    currentState,
                    null,
                    ctx.durationSec(),
                    null,
                    null,
                    null,
                    Map.of()
            );

            Map<String, Object> result = noosAiService.generateIntervention(request);
            String audioPath = extractAudioPath(result);
            if (audioPath == null || audioPath.isBlank()) {
                throw new IllegalStateException("audio path missing from NoosAiService result");
            }

            String audioId = audioRegistry.register(audioPath, ctx.adaptiveSessionId(), "audio/mpeg", ctx.durationSec());
            sessionSegmentMapper.updateStatus(segmentId, "ready", audioId, genStartedAt, Instant.now(), null, null);
        } catch (Exception e) {
            log.error("adaptive segment generation failed for {}", segmentId, e);
            String code = isAceStepFailure(e) ? "ACE_STEP_DOWN" : "GENERATION_FAILED";
            sessionSegmentMapper.updateStatus(segmentId, "failed", null, null, null, null, code);
        }
    }

    String extractAudioPath(Map<String, Object> result) {
        String topLevelAudioUrl = extractPathFromAudioReference(stringValue(result.get("audioUrl")));
        if (topLevelAudioUrl != null) {
            return topLevelAudioUrl;
        }

        Map<String, Object> interventionResult = mapValue(result.get("interventionResult"));
        String path = extractPathFromAceStepEntries(interventionResult);
        if (path != null) {
            return path;
        }

        for (String key : List.of("audio_file", "file_path", "audio_path", "local_file_path")) {
            String direct = extractPathFromAudioReference(stringValue(interventionResult.get(key)));
            if (direct != null) {
                return direct;
            }
        }
        return null;
    }

    private String extractPathFromAceStepEntries(Map<String, Object> interventionResult) {
        Map<String, Object> aceStepJob = mapValue(interventionResult.get("ace_step_job"));
        List<Map<String, Object>> entries = listOfMaps(aceStepJob.get("parsed_entries"));
        if (entries.isEmpty()) {
            return null;
        }

        return extractPathFromAudioReference(stringValue(entries.get(0).get("file")));
    }

    private String extractPathFromAudioReference(String reference) {
        if (reference == null) {
            return null;
        }
        if (!reference.contains("path=")) {
            if (reference.startsWith("http://") || reference.startsWith("https://")) {
                return null;
            }
            return reference;
        }

        String rawPath = reference.substring(reference.indexOf("path=") + 5);
        int ampersandIndex = rawPath.indexOf('&');
        if (ampersandIndex >= 0) {
            rawPath = rawPath.substring(0, ampersandIndex);
        }
        return URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
    }

    private boolean isAceStepFailure(Exception e) {
        if (e instanceof ResponseStatusException responseStatusException) {
            HttpStatusCode status = responseStatusException.getStatusCode();
            return status.is5xxServerError();
        }
        return false;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String string = String.valueOf(value);
        return string.isBlank() ? null : string;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }
}
