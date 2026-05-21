package com.noos.backend.auth.controller;

import com.noos.backend.auth.dto.AuthSessionResponse;
import com.noos.backend.auth.dto.SignupRequest;
import com.noos.backend.auth.dto.SimpleOkResponse;
import com.noos.backend.auth.dto.User;
import com.noos.backend.auth.session.SessionUser;
import com.noos.backend.auth.service.AuthSessionService;
import com.noos.backend.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthSessionService authSessionService;

    public AuthController(AuthService authService, AuthSessionService authSessionService) {
        this.authService = authService;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/signup")
    public SimpleOkResponse signup(@RequestBody SignupRequest request) {
        authService.signup(request);
        return new SimpleOkResponse(true);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(
            @RequestBody SignupRequest request,
            HttpServletRequest httpServletRequest
    ) {
        User user = authService.login(request);
        if (user == null) {
            return ResponseEntity.ok(AuthSessionResponse.anonymous());
        }

        SessionUser sessionUser = authSessionService.createLoginSession(httpServletRequest, user);
        return ResponseEntity.ok(AuthSessionResponse.fromSessionUser(sessionUser));
    }

    @GetMapping("/me")
    public AuthSessionResponse me(HttpServletRequest httpServletRequest) {
        return authSessionService.getCurrentSession(httpServletRequest);
    }

    @PostMapping("/logout")
    public SimpleOkResponse logout(HttpServletRequest httpServletRequest) {
        authSessionService.logout(httpServletRequest);
        return new SimpleOkResponse(true);
    }
}
