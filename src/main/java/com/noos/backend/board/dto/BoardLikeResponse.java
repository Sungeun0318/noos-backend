package com.noos.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BoardLikeResponse {
    private boolean liked;
    private int likes;
    private String message;
}
