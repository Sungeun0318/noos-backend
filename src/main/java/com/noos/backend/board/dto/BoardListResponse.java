package com.noos.backend.board.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class BoardListResponse {
    private List<BoardPost> posts;
    private int total;
    private int totalPages;
    private int page;
}
