package com.noos.backend.mobile.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noos.backend.mobile.common.ApiError;
import com.noos.backend.mobile.common.ApiErrorEnvelope;
import com.noos.backend.mobile.common.ErrorCode;
import com.noos.backend.mobile.common.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DeviceContextFilter extends OncePerRequestFilter {

    private static final String DEVICE_ID_HEADER = "x-device-id";
    private static final String HEALTH_PATH = "/api/mobile/health";

    private final ObjectMapper objectMapper;

    public DeviceContextFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (HEALTH_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String deviceId = request.getHeader(DEVICE_ID_HEADER);
        if (deviceId == null || deviceId.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), new ApiErrorEnvelope(new ApiError(
                    ErrorCode.MISSING_DEVICE_ID.name(),
                    ErrorCode.MISSING_DEVICE_ID.name(),
                    null
            )));
            return;
        }

        RequestContext.setDeviceId(deviceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }
}
