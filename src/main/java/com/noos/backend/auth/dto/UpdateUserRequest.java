package com.noos.backend.auth.dto;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String displayName;
    private String password;
    private String role;
}
