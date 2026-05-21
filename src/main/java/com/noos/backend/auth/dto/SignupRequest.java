package com.noos.backend.auth.dto;

import lombok.Data;

// 로컬 회원가입/로그인 요청에 사용하는 DTO
@Data
public class SignupRequest {
    private String loginId;
    private String password;
    private String passwordHash;
    private String displayName;
}
