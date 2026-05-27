package com.noos.backend.mobile.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "x-request-id";
    private static final String SERVER_TIME_HEADER = "x-server-time";
    private static final String MIN_APP_VERSION_HEADER = "x-min-app-version";
    private static final String MOBILE_PREFIX = "/api/mobile/";

    private final String minAppVersion;

    public RequestIdFilter(@Value("${noos.mobile.min-app-version:1.0.0}") String minAppVersion) {
        this.minAppVersion = minAppVersion;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isMobilePath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(SERVER_TIME_HEADER, Instant.now().toString());
        response.setHeader(MIN_APP_VERSION_HEADER, minAppVersion);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            MDC.remove("deviceId");
        }
    }

    private boolean isMobilePath(String uri) {
        return uri != null && uri.startsWith(MOBILE_PREFIX);
    }
}
