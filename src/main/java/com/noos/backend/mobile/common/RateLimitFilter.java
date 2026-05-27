package com.noos.backend.mobile.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String DEVICE_ID_HEADER = "x-device-id";
    private static final String MOBILE_PREFIX = "/api/mobile/";
    private static final String SESSIONS_PATH = "/api/mobile/sessions";

    private final Cache<String, TokenBucket> buckets;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Policy sessionsCreatePolicy;
    private final Policy defaultGetPolicy;

    public RateLimitFilter(ObjectMapper objectMapper,
                           MeterRegistry meterRegistry,
                           @Value("${noos.mobile.ratelimit.sessions-create.limit:6}") int sessionsCreateLimit,
                           @Value("${noos.mobile.ratelimit.sessions-create.per-minutes:10}") int sessionsCreateMinutes,
                           @Value("${noos.mobile.ratelimit.default-get.limit:600}") int defaultGetLimit,
                           @Value("${noos.mobile.ratelimit.default-get.per-minutes:1}") int defaultGetMinutes) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.sessionsCreatePolicy = new Policy(
                "sessions-create",
                Math.max(1, sessionsCreateLimit),
                Duration.ofMinutes(Math.max(1, sessionsCreateMinutes))
        );
        this.defaultGetPolicy = new Policy(
                "default-get",
                Math.max(1, defaultGetLimit),
                Duration.ofMinutes(Math.max(1, defaultGetMinutes))
        );
        Duration maxWindow = this.sessionsCreatePolicy.window().compareTo(this.defaultGetPolicy.window()) >= 0
                ? this.sessionsCreatePolicy.window()
                : this.defaultGetPolicy.window();
        this.buckets = Caffeine.newBuilder()
                .expireAfterWrite(Math.max(1, maxWindow.toMillis() * 3 / 2), TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Policy policy = policyFor(request);
        String deviceId = request.getHeader(DEVICE_ID_HEADER);
        if (policy == null || deviceId == null || deviceId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = policy.name() + ":" + deviceId;
        TokenBucket bucket = buckets.get(key, ignored -> new TokenBucket(policy.limit(), policy.window()));
        ConsumeResult result = bucket.tryConsume();
        if (result.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        meterRegistry.counter("noos.mobile.ratelimit.triggered.count", "policy", policy.name()).increment();
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSec()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiErrorEnvelope(new ApiError(
                ErrorCode.RATE_LIMITED.name(),
                "retry after " + result.retryAfterSec() + "s",
                null
        )));
    }

    private Policy policyFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (SESSIONS_PATH.equals(path) && "POST".equalsIgnoreCase(request.getMethod())) {
            return sessionsCreatePolicy;
        }
        if (path != null && path.startsWith(MOBILE_PREFIX) && "GET".equalsIgnoreCase(request.getMethod())) {
            return defaultGetPolicy;
        }
        return null;
    }

    private record Policy(String name, int limit, Duration window) {
    }

    private record ConsumeResult(boolean allowed, long retryAfterSec) {
    }

    private static final class TokenBucket {
        private final int limit;
        private final long windowNanos;
        private final double tokensPerNano;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(int limit, Duration window) {
            this.limit = limit;
            this.windowNanos = Math.max(1L, window.toNanos());
            this.tokensPerNano = (double) limit / windowNanos;
            this.tokens = limit;
            this.lastRefillNanos = System.nanoTime();
        }

        private synchronized ConsumeResult tryConsume() {
            long now = System.nanoTime();
            refill(now);
            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                return new ConsumeResult(true, 0);
            }
            double missing = 1.0d - tokens;
            long retryNanos = (long) Math.ceil(missing / tokensPerNano);
            long retryAfterSec = Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(retryNanos) + 1L);
            return new ConsumeResult(false, retryAfterSec);
        }

        private void refill(long now) {
            long elapsed = Math.max(0L, now - lastRefillNanos);
            if (elapsed == 0L) {
                return;
            }
            tokens = Math.min(limit, tokens + elapsed * tokensPerNano);
            lastRefillNanos = now;
        }
    }
}
