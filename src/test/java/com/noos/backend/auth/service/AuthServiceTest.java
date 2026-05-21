package com.noos.backend.auth.service;

import com.noos.backend.auth.dto.SignupRequest;
import com.noos.backend.auth.dto.User;
import com.noos.backend.auth.mapper.AuthMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final AuthMapper authMapper = mock(AuthMapper.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AuthService authService = new AuthService(authMapper, passwordEncoder);

    @Test
    void signupStoresHashedPassword() {
        SignupRequest request = new SignupRequest();
        request.setLoginId("tester");
        request.setPassword("plain-password");
        request.setDisplayName("Tester");

        authService.signup(request);

        verify(authMapper).insertUser(any(SignupRequest.class));
        assertNotEquals("plain-password", request.getPasswordHash());
        assertTrue(passwordEncoder.matches("plain-password", request.getPasswordHash()));
    }

    @Test
    void loginReturnsUserWhenPasswordMatchesHash() {
        SignupRequest request = new SignupRequest();
        request.setLoginId("tester");
        request.setPassword("plain-password");

        User user = new User();
        user.setLoginId("tester");
        user.setPasswordHash(passwordEncoder.encode("plain-password"));

        when(authMapper.findLocalUserByLoginId("tester")).thenReturn(user);

        assertSame(user, authService.login(request));
    }

    @Test
    void loginReturnsNullWhenPasswordDoesNotMatchHash() {
        SignupRequest request = new SignupRequest();
        request.setLoginId("tester");
        request.setPassword("wrong-password");

        User user = new User();
        user.setLoginId("tester");
        user.setPasswordHash(passwordEncoder.encode("plain-password"));

        when(authMapper.findLocalUserByLoginId("tester")).thenReturn(user);

        assertNull(authService.login(request));
    }
}
