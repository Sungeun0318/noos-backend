package com.noos.backend.mobile.common;

public enum ErrorCode {
    BAD_REQUEST(400),
    INVALID_CURRENT_STATE(400),
    INVALID_STATE_MEASUREMENT_PAYLOAD(400),
    EEG_INVALID_PAYLOAD(400),
    EEG_LOW_QUALITY(400),
    MISSING_DEVICE_ID(400),
    VALIDATION_FAILED(400),
    UNAUTHORIZED(401),
    INVALID_CREDENTIALS(401),
    AUDIO_SIGNATURE_INVALID(403),
    NOT_FOUND(404),
    SESSION_NOT_FOUND(404),
    ADAPTIVE_SESSION_NOT_FOUND(404),
    AUDIO_NOT_FOUND(404),
    AUDIO_FILE_NOT_FOUND(404),
    CONFLICT(409),
    ADAPTIVE_SESSION_STATE_CONFLICT(409),
    LOGIN_ID_TAKEN(409),
    IDEMPOTENCY_KEY_CONFLICT(409),
    AUDIO_EXPIRED(410),
    RATE_LIMITED(429),
    INTERNAL(500),
    INVALID_STORED_CURRENT_STATE(500),
    IDEMPOTENCY_RESPONSE_SERIALIZATION_FAILED(500),
    GENERATION_FAILED(500),
    ACE_STEP_DOWN(502),
    GENERATION_TIMEOUT(504),
    LIGHTING_DEVICE_NOT_FOUND(404),
    LIGHTING_DISABLED(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public static ErrorCode fromReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        try {
            return ErrorCode.valueOf(reason);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> AUDIO_SIGNATURE_INVALID;
            case 404 -> NOT_FOUND;
            case 409 -> CONFLICT;
            case 410 -> AUDIO_EXPIRED;
            case 429 -> RATE_LIMITED;
            default -> INTERNAL;
        };
    }
}
