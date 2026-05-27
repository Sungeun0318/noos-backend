package com.noos.backend.mobile.common;

public record ApiError(String code, String message, String requestId) {
}
