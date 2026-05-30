package com.noos.backend.mobile.session.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.mobile.common.ApiException;
import com.noos.backend.mobile.common.ErrorCode;
import com.noos.backend.mobile.session.dto.EnqueueRequest;
import com.noos.backend.mobile.session.dto.EnqueueResponse;
import com.noos.backend.mobile.session.dto.FeedbackRequest;
import com.noos.backend.mobile.session.dto.FeedbackResponse;
import com.noos.backend.mobile.session.dto.MobileSessionRow;
import com.noos.backend.mobile.session.dto.SessionFeedbackRow;
import com.noos.backend.mobile.session.dto.SessionListResponse;
import com.noos.backend.mobile.session.dto.SessionResponse;
import com.noos.backend.mobile.session.mapper.MobileSessionMapper;
import com.noos.backend.mobile.session.mapper.SessionFeedbackMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MobileSessionService {

    private static final TypeReference<Map<String, Double>> CURRENT_STATE_TYPE = new TypeReference<>() {
    };

    private final MobileSessionMapper sessionMapper;
    private final SessionFeedbackMapper feedbackMapper;
    private final ObjectMapper objectMapper;
    private final GenerationWorker worker;
    private final MeterRegistry meterRegistry;

    public MobileSessionService(MobileSessionMapper sessionMapper,
                                SessionFeedbackMapper feedbackMapper,
                                ObjectMapper objectMapper,
                                GenerationWorker worker,
                                MeterRegistry meterRegistry) {
        this.sessionMapper = sessionMapper;
        this.feedbackMapper = feedbackMapper;
        this.objectMapper = objectMapper;
        this.worker = worker;
        this.meterRegistry = meterRegistry;
    }

    public EnqueueResponse enqueue(EnqueueRequest request, String deviceId) {
        Instant now = Instant.now();
        String sessionId = "session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        MobileSessionRow row = new MobileSessionRow();
        row.setId(sessionId);
        row.setDeviceId(deviceId);
        row.setPlanet(request.planet());
        row.setDurationSec(request.durationSec());
        row.setStateLabel(request.stateLabel());
        row.setCurrentState(writeJson(request.currentState()));
        row.setIntentText(request.intentText());
        row.setRecognitionId(request.measurementId());
        row.setSource(request.source());
        row.setLightingEnabled(request.lightingEnabled());
        row.setStatus("queued");
        row.setCreatedAt(now);
        sessionMapper.insertQueued(row);

        GenerationContext ctx = new GenerationContext(
                request.planet(),
                request.durationSec(),
                request.currentState(),
                request.stateLabel(),
                request.intentText(),
                request.source(),
                request.lightingEnabled()
        );
        worker.run(sessionId, ctx);
        meterRegistry.counter(
                "noos.mobile.session.enqueue.count",
                "planet", metricValue(request.planet()),
                "source", metricValue(request.source())
        ).increment();

        return new EnqueueResponse(sessionId, "queued", request.planet(), request.durationSec(), 600, 5000, now);
    }

    public SessionResponse get(String sessionId, String deviceId) {
        MobileSessionRow row = findVisibleSession(sessionId, deviceId);
        return toResponse(row);
    }

    public SessionListResponse list(String deviceId, String cursor, int limit, List<String> status) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<SessionResponse> items = sessionMapper.list(deviceId, null, cursor, safeLimit, status).stream()
                .map(this::toResponse)
                .toList();
        return new SessionListResponse(items, null, false);
    }

    public void delete(String sessionId, String deviceId) {
        findVisibleSession(sessionId, deviceId);
        sessionMapper.softDelete(sessionId);
    }

    public FeedbackResponse submitFeedback(String sessionId, String deviceId, FeedbackRequest request) {
        findVisibleSession(sessionId, deviceId);
        Instant now = Instant.now();

        SessionFeedbackRow row = new SessionFeedbackRow();
        row.setSessionId(sessionId);
        row.setMusicFit(request.musicFit());
        row.setLightingFit(request.lightingFit());
        row.setFocusResult(request.focusResult());
        row.setMemo(request.memo());
        row.setCreatedAt(now);
        feedbackMapper.upsert(row);

        return new FeedbackResponse(true, now);
    }

    private MobileSessionRow findVisibleSession(String sessionId, String deviceId) {
        MobileSessionRow row = sessionMapper.findById(sessionId);
        if (row == null || row.getDeletedAt() != null || !deviceId.equals(row.getDeviceId())) {
            throw new ApiException(ErrorCode.SESSION_NOT_FOUND);
        }
        return row;
    }

    private SessionResponse toResponse(MobileSessionRow row) {
        return new SessionResponse(
                row.getId(),
                row.getStatus(),
                row.getPlanet(),
                row.getDurationSec(),
                row.getStateLabel(),
                readJson(row.getCurrentState()),
                audioInfo(row),
                null,
                null,
                null,
                null,
                row.getCreatedAt(),
                row.getStartedAt(),
                row.getCompletedAt()
        );
    }

    private SessionResponse.AudioInfo audioInfo(MobileSessionRow row) {
        if (row.getAudioId() == null || row.getAudioId().isBlank()) {
            return null;
        }
        return new SessionResponse.AudioInfo(row.getAudioId(), row.getDurationSec());
    }

    private String writeJson(Map<String, Double> currentState) {
        if (currentState == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(currentState);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INVALID_CURRENT_STATE, ErrorCode.INVALID_CURRENT_STATE.name(), e);
        }
    }

    private Map<String, Double> readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CURRENT_STATE_TYPE);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INVALID_STORED_CURRENT_STATE, ErrorCode.INVALID_STORED_CURRENT_STATE.name(), e);
        }
    }

    private String metricValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
