package com.noos.backend.mobile.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = "com.noos.backend.mobile")
public class ApiErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorAdvice.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorEnvelope> handleApiException(ApiException e, HttpServletRequest request) {
        return response(e.code, e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorEnvelope> handleResponseStatusException(ResponseStatusException e,
                                                                          HttpServletRequest request) {
        int status = e.getStatusCode().value();
        ErrorCode code = ErrorCode.fromReason(e.getReason());
        if (code == null) {
            code = ErrorCode.fromStatus(status);
        }
        String message = e.getReason() == null || e.getReason().isBlank() ? code.name() : e.getReason();
        return response(code, message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorEnvelope> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_FAILED.name());
        return response(ErrorCode.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMissingRequestHeader(MissingRequestHeaderException e) {
        ErrorCode code = "x-device-id".equalsIgnoreCase(e.getHeaderName())
                ? ErrorCode.MISSING_DEVICE_ID
                : ErrorCode.BAD_REQUEST;
        return response(code, code.name());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorEnvelope> handleUnreadable(HttpMessageNotReadableException e) {
        return response(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.name());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorEnvelope> handleException(Exception e, HttpServletRequest request) {
        log.error("Unhandled mobile API exception", e);
        return response(ErrorCode.INTERNAL, "internal server error");
    }

    private ResponseEntity<ApiErrorEnvelope> response(ErrorCode code, String message) {
        return ResponseEntity.status(HttpStatus.valueOf(code.httpStatus()))
                .body(new ApiErrorEnvelope(new ApiError(code.name(), message, requestId())));
    }

    private String requestId() {
        String requestId = MDC.get("requestId");
        return requestId == null || requestId.isBlank() ? null : requestId;
    }
}
