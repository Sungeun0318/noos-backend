package com.noos.backend.board.dto;

import lombok.Data;

@Data
public class BoardRequest {
    private String category;
    private String title;
    private String content;
    private Boolean pinned;
}
