package com.noos.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BoardPostDetailResponse {
    private BoardPost post;
    private boolean liked;
}
