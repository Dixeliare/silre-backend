package com.longdx.silre_backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating a post
 * 
 * Pattern:
 * - All fields optional (only update provided fields)
 * - Use @Valid for validation
 * - Trim and normalize input
 */
public record UpdatePostRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 10000, message = "Content must not exceed 10000 characters")
        String content,

        // Note: slug is auto-generated from title/content, user should not provide it
        @Deprecated
        @Schema(hidden = true)
        @Size(max = 350, message = "Slug must not exceed 350 characters")
        String slug,  // Deprecated: Auto-generated from title, ignored if provided

        Boolean isNsfw
) {
    public UpdatePostRequest {
        if (title != null) { title = title.trim(); }
        if (content != null) { content = content.trim(); }
        if (slug != null) { slug = slug.trim(); }
    }
}
