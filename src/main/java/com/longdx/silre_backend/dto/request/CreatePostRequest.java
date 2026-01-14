package com.longdx.silre_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new post
 * 
 * Pattern:
 * - Use @Valid for validation
 * - Trim and normalize input in compact constructor
 * - Separate DTOs for different operations
 */
public record CreatePostRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,  // Optional for social posts

        @NotBlank(message = "Content is required")
        @Size(max = 10000, message = "Content must not exceed 10000 characters")
        String content,

        String communityPublicId,  // Optional: if provided, post belongs to community
        String topicSlug,  // Optional: for personal posts with topic

        @Size(max = 350, message = "Slug must not exceed 350 characters")
        String slug,  // Optional: for SEO

        Boolean isNsfw  // Optional: defaults to false
) {
    public CreatePostRequest {
        if (title != null) { title = title.trim(); }
        if (content != null) { content = content.trim(); }
        if (communityPublicId != null) { communityPublicId = communityPublicId.trim(); }
        if (topicSlug != null) { topicSlug = topicSlug.trim(); }
        if (slug != null) { slug = slug.trim(); }
        if (isNsfw == null) { isNsfw = false; }
    }
}
