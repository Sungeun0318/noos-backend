package com.noos.backend.board.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BoardComment {
    private Long id;
    private Long postId;
    private Long authorId;
    private String author;
    private String content;
    private int likes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BoardComment create(Long postId, Long authorId, String content) {
        BoardComment comment = new BoardComment();
        comment.postId = postId;
        comment.authorId = authorId;
        comment.content = content.trim();
        comment.createdAt = LocalDateTime.now();
        return comment;
    }
}
