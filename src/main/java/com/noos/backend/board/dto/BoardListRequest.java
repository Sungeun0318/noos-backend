package com.noos.backend.board.dto;

import lombok.Data;

@Data
public class BoardListRequest {
    private String category = "ALL";
    private String search = "";
    private String sort = "latest";
    private int page = 1;
    private int size = 8;
}
