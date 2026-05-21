package com.noos.backend.auth.dto;

import lombok.Data;

@Data
public class UserSearchRequest {
    private String type = "";
    private String keyword = "";
    private String startDate = "";
    private String endDate = "";
}
