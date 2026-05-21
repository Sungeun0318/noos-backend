package com.noos.backend.board.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BoardPost {
    private Long id;
    private String category;
    private String title;
    private String content;
    private String author;
    private Long authorId;
    private int views;
    private int likes;
    private int commentCount;
    private boolean pinned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BoardPost create(BoardRequest request, Long authorId) {
        BoardPost post = new BoardPost();
        post.category = request.getCategory() != null ? request.getCategory() : "FREE";
        post.title = request.getTitle().trim();
        post.content = request.getContent().trim();
        post.pinned = Boolean.TRUE.equals(request.getPinned());
        post.authorId = authorId;
        post.createdAt = LocalDateTime.now();
        return post;
    }

    public void updateFrom(BoardRequest request) {
        if (request.getCategory() != null) {
            this.category = request.getCategory();
        }
        if (request.getTitle() != null) {
            this.title = request.getTitle().trim();
        }
        if (request.getContent() != null) {
            this.content = request.getContent().trim();
        }
        if (request.getPinned() != null) {
            this.pinned = request.getPinned();
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void increaseViews() {
        this.views++;
    }
}
