package com.noos.backend.board.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.noos.backend.board.dto.BoardComment;
import com.noos.backend.board.dto.BoardPost;

import java.util.List;

/**
 * BoardMapper.java
 * SQL은 resources/mappers/board/BoardMapper.xml 에 작성
 */
@Mapper
public interface BoardMapper {

    // ── 게시글 CRUD ───────────────────────────────────────────────────────────

    /** 게시글 목록 조회 */
    List<BoardPost> findPosts(
        @Param("category")    String category,
        @Param("searchQuery") String searchQuery,
        @Param("sortBy")      String sortBy,
        @Param("offset")      int    offset,
        @Param("limit")       int    limit
    );

    /** 전체 게시글 수 */
    int countPosts(
        @Param("category")    String category,
        @Param("searchQuery") String searchQuery
    );

    /** 게시글 단건 조회 */
    BoardPost findPostById(@Param("id") Long id);

    /** 게시글 등록 */
    void insertPost(BoardPost post);

    /** 게시글 수정 */
    void updatePost(BoardPost post);

    /** 게시글 삭제 */
    void deletePost(@Param("id") Long id);

    /** 조회수 1 증가 */
    void incrementViews(@Param("id") Long id);

    /** 게시글 좋아요 수 1 증가 */
    void incrementLikes(@Param("id") Long id);

    /** 게시글 좋아요 수 1 감소 (최솟값 0) */
    void decrementLikes(@Param("id") Long id);

    /** 댓글 수 1 증가 */
    void incrementCommentCount(@Param("id") Long id);

    /** 댓글 수 1 감소 (최솟값 0) */
    void decrementCommentCount(@Param("id") Long id);

    // ── 게시글 좋아요 (board_post_likes) ─────────────────────────────────────

    /** 유저가 게시글에 좋아요 눌렀는지 확인 (0 or 1) */
    int checkUserLiked(@Param("userId") Long userId, @Param("postId") Long postId);

    /** 게시글 좋아요 INSERT */
    void insertUserLike(@Param("userId") Long userId, @Param("postId") Long postId);

    /** 게시글 좋아요 DELETE */
    void deleteUserLike(@Param("userId") Long userId, @Param("postId") Long postId);

    // ── 댓글 CRUD ─────────────────────────────────────────────────────────────

    /** 특정 게시글의 댓글 목록 조회 */
    List<BoardComment> findCommentsByPostId(@Param("postId") Long postId);

    /** 댓글 등록 */
    void insertComment(BoardComment comment);

    /** 댓글 단건 조회 */
    BoardComment findCommentById(@Param("id") Long id);

    /** 댓글 삭제 */
    void deleteComment(@Param("id") Long id);

    /** 댓글 좋아요 수 1 증가 */
    void incrementCommentLikes(@Param("id") Long id);

    /** 댓글 좋아요 수 1 감소 (최솟값 0) */
    void decrementCommentLikes(@Param("id") Long id);

    // ── 댓글 좋아요 (board_comment_likes) ────────────────────────────────────

    /** 유저가 댓글에 좋아요 눌렀는지 확인 (0 or 1) */
    int checkUserCommentLiked(@Param("userId") Long userId, @Param("commentId") Long commentId);

    /** 댓글 좋아요 INSERT */
    void insertUserCommentLike(@Param("userId") Long userId, @Param("commentId") Long commentId);

    /** 댓글 좋아요 DELETE */
    void deleteUserCommentLike(@Param("userId") Long userId, @Param("commentId") Long commentId);
}