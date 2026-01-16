package com.longdx.silre_backend.repository;

import com.longdx.silre_backend.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Find comments by post (cursor-based pagination)
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId ORDER BY c.createdAt DESC, c.id DESC")
    Page<Comment> findByPost_Id(@Param("postId") Long postId, Pageable pageable);

    // Find comments by author
    Page<Comment> findByAuthor_InternalId(Long authorId, Pageable pageable);

    // Find root comments (no parent) for a post (Instagram-Style - Flat)
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId AND c.parentComment IS NULL ORDER BY c.createdAt DESC, c.id DESC")
    Page<Comment> findRootCommentsByPost(@Param("postId") Long postId, Pageable pageable);

    // Find replies to a comment (load tại chỗ khi bấm "Xem thêm")
    @Query("SELECT c FROM Comment c WHERE c.parentComment.id = :parentCommentId ORDER BY c.createdAt ASC, c.id ASC")
    List<Comment> findRepliesByParentComment(@Param("parentCommentId") Long parentCommentId);

    // Count comments by post
    long countByPost_Id(Long postId);

    // Count root comments (no parent) by post
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.post.id = :postId AND c.parentComment IS NULL")
    long countRootCommentsByPost(@Param("postId") Long postId);
}

