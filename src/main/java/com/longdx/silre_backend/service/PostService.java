package com.longdx.silre_backend.service;

import com.longdx.silre_backend.dto.request.CreatePostRequest;
import com.longdx.silre_backend.dto.request.UpdatePostRequest;
import com.longdx.silre_backend.dto.response.PostResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for Post operations
 * 
 * Pattern:
 * - Business logic separation
 * - Transaction management
 * - Authorization checks
 */
public interface PostService {
    
    /**
     * Create a new post
     * 
     * @param request Create post request
     * @param authorId Current user ID (from authentication)
     * @return Created post response
     */
    PostResponse createPost(CreatePostRequest request, Long authorId);
    
    /**
     * Get post by public ID
     * 
     * @param publicId Post public ID
     * @param currentUserId Current user ID (null if not authenticated)
     * @return Post response
     */
    PostResponse getPostByPublicId(String publicId, Long currentUserId);
    
    /**
     * Update post
     * 
     * @param publicId Post public ID
     * @param request Update request
     * @param currentUserId Current user ID (must be author)
     * @return Updated post response
     */
    PostResponse updatePost(String publicId, UpdatePostRequest request, Long currentUserId);
    
    /**
     * Delete post
     * 
     * @param publicId Post public ID
     * @param currentUserId Current user ID (must be author)
     */
    void deletePost(String publicId, Long currentUserId);
    
    /**
     * Like a post
     * 
     * @param publicId Post public ID
     * @param userId User ID who likes
     */
    void likePost(String publicId, Long userId);
    
    /**
     * Unlike a post
     * 
     * @param publicId Post public ID
     * @param userId User ID who unlikes
     */
    void unlikePost(String publicId, Long userId);
    
    /**
     * Get feed posts (all posts, ordered by creation date)
     * 
     * @param pageable Pagination
     * @param currentUserId Current user ID (null if not authenticated)
     * @return Page of posts
     */
    Page<PostResponse> getFeed(Pageable pageable, Long currentUserId);
    
    /**
     * Get posts by user
     * 
     * @param userPublicId User public ID
     * @param pageable Pagination
     * @param currentUserId Current user ID (null if not authenticated)
     * @return Page of posts
     */
    Page<PostResponse> getPostsByUser(String userPublicId, Pageable pageable, Long currentUserId);
    
    /**
     * Get posts by community
     * 
     * @param communityPublicId Community public ID
     * @param pageable Pagination
     * @param currentUserId Current user ID (null if not authenticated)
     * @return Page of posts
     */
    Page<PostResponse> getPostsByCommunity(String communityPublicId, Pageable pageable, Long currentUserId);
    
    /**
     * Get personal posts (community_id IS NULL) by user
     * 
     * @param userPublicId User public ID
     * @param pageable Pagination
     * @param currentUserId Current user ID (null if not authenticated)
     * @return Page of posts
     */
    Page<PostResponse> getPersonalPostsByUser(String userPublicId, Pageable pageable, Long currentUserId);
}
