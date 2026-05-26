package com.noos.backend.mobile.auth.dto;

import lombok.Data;

@Data
public class MobileUserRow {
    private Long userId;
    private String loginId;
    private String passwordHash;
    private String displayName;
}
