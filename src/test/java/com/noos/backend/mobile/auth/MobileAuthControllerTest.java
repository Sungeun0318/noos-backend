package com.noos.backend.mobile.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class MobileAuthControllerTest {

    private static final String DEVICE_ID = "dev_auth_test_001";
    private static final String LOGIN_PREFIX = "mobile_auth_test_";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("""
                DELETE mat
                FROM mobile_auth_tokens mat
                JOIN users u ON u.user_id = mat.user_id
                WHERE u.login_id LIKE ?
                """, LOGIN_PREFIX + "%");
        jdbc.update("DELETE FROM users WHERE login_id LIKE ?", LOGIN_PREFIX + "%");
    }

    @Test
    void signupReturnsTokensAndStoresRefreshHash() throws Exception {
        AuthResult auth = signup(uniqueLogin());

        assertThat(auth.accessToken()).isNotBlank();
        assertThat(auth.refreshToken()).isNotBlank();
        assertThat(auth.expiresIn()).isEqualTo(900);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mobile_auth_tokens WHERE user_id = ? AND device_id = ?",
                Integer.class,
                auth.userId(),
                DEVICE_ID
        );
        String storedRefresh = jdbc.queryForObject(
                "SELECT refresh_token FROM mobile_auth_tokens WHERE user_id = ?",
                String.class,
                auth.userId()
        );

        assertThat(rows).isEqualTo(1);
        assertThat(storedRefresh).isEqualTo(sha256(auth.refreshToken()));
        assertThat(storedRefresh).isNotEqualTo(auth.refreshToken());
    }

    @Test
    void signupDuplicateLoginIdReturnsConflict() throws Exception {
        String loginId = uniqueLogin();
        signup(loginId);

        mockMvc.perform(post("/api/mobile/auth/signup")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "loginId", loginId,
                                "password", "password-2",
                                "displayName", "Duplicate"
                        ))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void loginReturnsNewTokens() throws Exception {
        String loginId = uniqueLogin();
        AuthResult signup = signup(loginId);

        MvcResult result = mockMvc.perform(post("/api/mobile/auth/login")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "loginId", loginId,
                                "password", "password-1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userId").value(signup.userId()))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        AuthResult login = parseAuth(result);
        assertThat(login.accessToken()).isNotEqualTo(signup.accessToken());
        assertThat(login.refreshToken()).isNotEqualTo(signup.refreshToken());
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        String loginId = uniqueLogin();
        signup(loginId);

        mockMvc.perform(post("/api/mobile/auth/login")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "loginId", loginId,
                                "password", "wrong-password"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutAuthorizationReturnsGuest() throws Exception {
        mockMvc.perform(get("/api/mobile/me")
                        .header("x-device-id", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("guest"))
                .andExpect(jsonPath("$.deviceId").value(DEVICE_ID))
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void meWithBearerTokenReturnsAuthedUser() throws Exception {
        AuthResult auth = signup(uniqueLogin());

        mockMvc.perform(get("/api/mobile/me")
                        .header("x-device-id", DEVICE_ID)
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("authed"))
                .andExpect(jsonPath("$.deviceId").value(DEVICE_ID))
                .andExpect(jsonPath("$.user.userId").value(auth.userId()))
                .andExpect(jsonPath("$.user.loginId", startsWith(LOGIN_PREFIX)));
    }

    @Test
    void refreshRotatesRefreshTokenAndRevokesOldToken() throws Exception {
        AuthResult old = signup(uniqueLogin());

        AuthResult rotated = refresh(old.refreshToken());

        assertThat(rotated.accessToken()).isNotEqualTo(old.accessToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(old.refreshToken());

        Integer oldRevoked = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mobile_auth_tokens WHERE refresh_token = ? AND revoked_at IS NOT NULL",
                Integer.class,
                sha256(old.refreshToken())
        );
        assertThat(oldRevoked).isEqualTo(1);

        mockMvc.perform(post("/api/mobile/auth/refresh")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", old.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenReturnsUnauthorized() throws Exception {
        AuthResult auth = signup(uniqueLogin());
        String expiredRefresh = "expired-refresh-token";
        jdbc.update("""
                INSERT INTO mobile_auth_tokens (
                    id, user_id, device_id, access_jti, refresh_token, expires_at, created_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?
                )
                """,
                "maut_expired_" + System.nanoTime(),
                auth.userId(),
                DEVICE_ID,
                "jti_expired_" + System.nanoTime(),
                sha256(expiredRefresh),
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                java.sql.Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS))
        );

        mockMvc.perform(post("/api/mobile/auth/refresh")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", expiredRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTokenAndMeFallsBackToGuest() throws Exception {
        AuthResult auth = signup(uniqueLogin());

        mockMvc.perform(post("/api/mobile/auth/logout")
                        .header("x-device-id", DEVICE_ID)
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        Integer revoked = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mobile_auth_tokens WHERE user_id = ? AND revoked_at IS NOT NULL",
                Integer.class,
                auth.userId()
        );
        assertThat(revoked).isEqualTo(1);

        mockMvc.perform(get("/api/mobile/me")
                        .header("x-device-id", DEVICE_ID)
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("guest"))
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    private AuthResult signup(String loginId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mobile/auth/signup")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "loginId", loginId,
                                "password", "password-1",
                                "displayName", "Mobile User"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.loginId").value(loginId))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();
        return parseAuth(result);
    }

    private AuthResult refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mobile/auth/refresh")
                        .header("x-device-id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        return parseAuth(result);
    }

    private AuthResult parseAuth(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthResult(
                root.get("user").get("userId").asLong(),
                root.get("accessToken").asText(),
                root.get("refreshToken").asText(),
                root.get("expiresIn").asLong()
        );
    }

    private String uniqueLogin() {
        return LOGIN_PREFIX + System.nanoTime();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String sha256(String token) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private record AuthResult(Long userId, String accessToken, String refreshToken, long expiresIn) {
    }
}
