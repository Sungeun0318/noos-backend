package com.noos.backend.board.service;

import com.noos.backend.auth.service.AuthSessionService;
import com.noos.backend.auth.session.SessionUser;
import com.noos.backend.board.dto.BoardComment;
import com.noos.backend.board.dto.BoardLikeResponse;
import com.noos.backend.board.dto.BoardListRequest;
import com.noos.backend.board.dto.BoardListResponse;
import com.noos.backend.board.dto.BoardPost;
import com.noos.backend.board.dto.BoardPostDetailResponse;
import com.noos.backend.board.dto.BoardRequest;
import com.noos.backend.board.mapper.BoardMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BoardService {

    private final BoardMapper boardMapper;
    private final AuthSessionService authSessionService;

    public BoardService(BoardMapper boardMapper, AuthSessionService authSessionService) {
        this.boardMapper = boardMapper;
        this.authSessionService = authSessionService;
    }

    @Transactional(readOnly = true)
    public BoardListResponse getPosts(BoardListRequest request) {
        String category = request.getCategory();
        String searchQuery = request.getSearch();
        String sortBy = request.getSort();
        int page = request.getPage();
        int size = request.getSize();

        String categoryFilter = category == null || "ALL".equals(category) ? null : category;
        String searchFilter = searchQuery == null || searchQuery.isBlank() ? null : searchQuery.trim();
        int resolvedPage = Math.max(1, page);
        int resolvedSize = Math.max(1, size);
        int offset = (resolvedPage - 1) * resolvedSize;

        List<BoardPost> posts = boardMapper.findPosts(categoryFilter, searchFilter, sortBy, offset, resolvedSize);
        int total = boardMapper.countPosts(categoryFilter, searchFilter);
        int totalPages = (int) Math.ceil((double) total / resolvedSize);

        return new BoardListResponse(posts, total, totalPages, resolvedPage);
    }

    @Transactional
    public BoardPostDetailResponse getPostWithLike(Long id, HttpSession session) {
        BoardPost post = requirePost(id);
        boardMapper.incrementViews(id);
        post.increaseViews();

        Long userId = currentUserIdOrNull(session);
        boolean liked = userId != null && boardMapper.checkUserLiked(userId, id) > 0;
        return new BoardPostDetailResponse(post, liked);
    }

    @Transactional
    public BoardPost createPost(BoardRequest request, HttpSession session) {
        Long authorId = requireUserId(session);
        validatePostRequest(request, true);

        BoardPost post = BoardPost.create(request, authorId);
        boardMapper.insertPost(post);
        return post;
    }

    @Transactional
    public BoardPost updatePost(Long id, BoardRequest request, HttpSession session) {
        Long requesterId = requireUserId(session);
        BoardPost existing = requirePost(id);
        requireOwnerOrAdmin(existing.getAuthorId(), requesterId, session, "No permission to update this post.");
        validatePostRequest(request, false);

        existing.updateFrom(request);

        boardMapper.updatePost(existing);
        return existing;
    }

    @Transactional
    public void deletePost(Long id, HttpSession session) {
        Long requesterId = requireUserId(session);
        BoardPost existing = requirePost(id);
        requireOwnerOrAdmin(existing.getAuthorId(), requesterId, session, "No permission to delete this post.");
        boardMapper.deletePost(id);
    }

    @Transactional
    public BoardLikeResponse toggleLike(Long postId, HttpSession session) {
        Long userId = requireUserId(session);
        requirePost(postId);

        boolean alreadyLiked = boardMapper.checkUserLiked(userId, postId) > 0;
        boolean liked;
        String message;

        if (alreadyLiked) {
            boardMapper.deleteUserLike(userId, postId);
            boardMapper.decrementLikes(postId);
            liked = false;
            message = "Post like canceled.";
        } else {
            boardMapper.insertUserLike(userId, postId);
            boardMapper.incrementLikes(postId);
            liked = true;
            message = "Post liked.";
        }

        BoardPost updated = boardMapper.findPostById(postId);
        int likes = updated != null ? updated.getLikes() : 0;
        return new BoardLikeResponse(liked, likes, message);
    }

    @Transactional(readOnly = true)
    public List<BoardComment> getComments(Long postId) {
        requirePost(postId);
        return boardMapper.findCommentsByPostId(postId);
    }

    @Transactional
    public BoardComment addComment(Long postId, String content, HttpSession session) {
        Long authorId = requireUserId(session);
        requirePost(postId);
        validateCommentContent(content);

        BoardComment comment = BoardComment.create(postId, authorId, content);
        boardMapper.insertComment(comment);
        boardMapper.incrementCommentCount(postId);
        return comment;
    }

    @Transactional
    public void deleteComment(Long commentId, HttpSession session) {
        Long requesterId = requireUserId(session);
        BoardComment comment = requireComment(commentId);
        requireOwnerOrAdmin(comment.getAuthorId(), requesterId, session, "No permission to delete this comment.");

        boardMapper.deleteComment(commentId);
        boardMapper.decrementCommentCount(comment.getPostId());
    }

    @Transactional
    public BoardLikeResponse toggleCommentLike(Long commentId, HttpSession session) {
        Long userId = requireUserId(session);
        requireComment(commentId);

        boolean alreadyLiked = boardMapper.checkUserCommentLiked(userId, commentId) > 0;
        boolean liked;
        String message;

        if (alreadyLiked) {
            boardMapper.deleteUserCommentLike(userId, commentId);
            boardMapper.decrementCommentLikes(commentId);
            liked = false;
            message = "Comment like canceled.";
        } else {
            boardMapper.insertUserCommentLike(userId, commentId);
            boardMapper.incrementCommentLikes(commentId);
            liked = true;
            message = "Comment liked.";
        }

        BoardComment updated = boardMapper.findCommentById(commentId);
        int likes = updated != null ? updated.getLikes() : 0;
        return new BoardLikeResponse(liked, likes, message);
    }

    private BoardPost requirePost(Long id) {
        BoardPost post = boardMapper.findPostById(id);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found.");
        }
        return post;
    }

    private BoardComment requireComment(Long id) {
        BoardComment comment = boardMapper.findCommentById(id);
        if (comment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found.");
        }
        return comment;
    }

    private Long requireUserId(HttpSession session) {
        Long userId = currentUserIdOrNull(session);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login is required.");
        }
        return userId;
    }

    private Long currentUserIdOrNull(HttpSession session) {
        SessionUser sessionUser = authSessionService.getSessionUser(session);
        return sessionUser != null ? sessionUser.userId() : null;
    }

    private boolean isAdmin(HttpSession session) {
        SessionUser sessionUser = authSessionService.getSessionUser(session);
        return sessionUser != null && sessionUser.isAdmin();
    }

    private void requireOwnerOrAdmin(Long ownerId, Long requesterId, HttpSession session, String message) {
        if (!isAdmin(session) && !ownerId.equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private void validatePostRequest(BoardRequest request, boolean requireContent) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        if (requireContent || request.getTitle() != null) {
            if (request.getTitle() == null || request.getTitle().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required.");
            }
            if (request.getTitle().length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title must be 100 characters or less.");
            }
        }
        if (requireContent || request.getContent() != null) {
            if (request.getContent() == null || request.getContent().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content is required.");
            }
            if (request.getContent().length() > 5000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content must be 5000 characters or less.");
            }
        }
    }

    private void validateCommentContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment content is required.");
        }
        if (content.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment must be 1000 characters or less.");
        }
    }
}
