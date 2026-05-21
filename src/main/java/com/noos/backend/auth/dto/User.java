package com.noos.backend.auth.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Long userId;        // user_id
    private String loginId;     // login_id
    private String passwordHash; // password_hash (NULL 허용)
    private String displayName;  // display_name
    private String provider;     // provider (local, google 등)
    private String providerId;   // provider_id
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String role;
}
