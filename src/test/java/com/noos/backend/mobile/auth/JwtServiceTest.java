package com.noos.backend.mobile.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.noos.backend.mobile.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void issueAndParseRoundTrip() {
        JwtService jwtService = new JwtService(SECRET, 15);

        String token = jwtService.issueAccess(42L, "dev_jwt_test", "jti_test");
        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("did", String.class)).isEqualTo("dev_jwt_test");
        assertThat(claims.getId()).isEqualTo("jti_test");
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwtService = new JwtService(SECRET, -1);

        String token = jwtService.issueAccess(42L, "dev_jwt_test", "jti_expired");

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(JwtException.class);
    }
}
