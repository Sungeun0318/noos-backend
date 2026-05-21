package com.noos.backend.auth.service;

import com.noos.backend.auth.dto.SignupRequest;
import com.noos.backend.auth.dto.User;
import com.noos.backend.auth.mapper.AuthMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthMapper authMapper, PasswordEncoder passwordEncoder) {
        this.authMapper = authMapper;
        this.passwordEncoder = passwordEncoder;
    }

    // 회원가입 시 비밀번호를 해시해서 저장
    public void signup(SignupRequest request) {
        request.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        authMapper.insertUser(request);
    }

    // 로그인 시 입력 비밀번호와 저장된 해시를 비교
    public User login(SignupRequest request) {
        User user = authMapper.findLocalUserByLoginId(request.getLoginId());
        if (user == null || user.getPasswordHash() == null) {
            return null;
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return null;
        }

        return user;
    }
}
