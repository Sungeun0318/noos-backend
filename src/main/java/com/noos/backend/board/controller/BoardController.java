package com.noos.backend.board.controller;

import com.noos.backend.board.dto.BoardComment;
import com.noos.backend.board.dto.BoardCommentRequest;
import com.noos.backend.board.dto.BoardLikeResponse;
import com.noos.backend.board.dto.BoardListRequest;
import com.noos.backend.board.dto.BoardListResponse;
import com.noos.backend.board.dto.BoardPost;
import com.noos.backend.board.dto.BoardPostDetailResponse;
import com.noos.backend.board.dto.BoardRequest;
import com.noos.backend.board.service.BoardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth/board")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping
    public ResponseEntity<BoardListResponse> getPosts(BoardListRequest request) {
        return ResponseEntity.ok(boardService.getPosts(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BoardPostDetailResponse> getPost(@PathVariable Long id, HttpSession session) {
        return ResponseEntity.ok(boardService.getPostWithLike(id, session));
    }

    @PostMapping
    public ResponseEntity<BoardPost> createPost(@RequestBody BoardRequest request, HttpSession session) {
        return ResponseEntity.ok(boardService.createPost(request, session));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BoardPost> updatePost(
            @PathVariable Long id,
            @RequestBody BoardRequest request,
            HttpSession session
    ) {
        return ResponseEntity.ok(boardService.updatePost(id, request, session));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePost(@PathVariable Long id, HttpSession session) {
        boardService.deletePost(id, session);
        return ResponseEntity.ok("Deleted.");
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<BoardLikeResponse> likePost(@PathVariable Long id, HttpSession session) {
        return ResponseEntity.ok(boardService.toggleLike(id, session));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<BoardComment>> getComments(@PathVariable Long id) {
        return ResponseEntity.ok(boardService.getComments(id));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<BoardComment> addComment(
            @PathVariable Long id,
            @RequestBody BoardCommentRequest request,
            HttpSession session
    ) {
        return ResponseEntity.ok(boardService.addComment(id, request.getContent(), session));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<String> deleteComment(@PathVariable Long commentId, HttpSession session) {
        boardService.deleteComment(commentId, session);
        return ResponseEntity.ok("Comment deleted.");
    }

    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<BoardLikeResponse> likeComment(
            @PathVariable Long commentId,
            HttpSession session
    ) {
        return ResponseEntity.ok(boardService.toggleCommentLike(commentId, session));
    }
}
