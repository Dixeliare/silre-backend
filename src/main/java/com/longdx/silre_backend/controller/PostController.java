package com.longdx.silre_backend.controller;

import com.longdx.silre_backend.dto.request.CreatePostRequest;
import com.longdx.silre_backend.dto.request.UpdatePostRequest;
import com.longdx.silre_backend.dto.response.PostResponse;
import com.longdx.silre_backend.dto.response.StandardResponse;
import com.longdx.silre_backend.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Post Controller
 * 
 * Handles post-related endpoints: create, read, update, delete, like.
 * 
 * Pattern:
 * - @RestController annotation
 * - @RequestMapping for base path
 * - Use DTOs for request/response
 * - @Valid for request validation
 * - Return ResponseEntity for status control
 * - Use proper HTTP status codes
 * - Extract current user from Authentication
 */
@RestController
@RequestMapping("/api/v1/posts")
@Tag(name = "Posts", description = "Post management APIs - Create, read, update, delete, and like posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * Get current user ID from Authentication
     * 
     * @param authentication Spring Security Authentication object
     * @return User ID or null if not authenticated
     */
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @PostMapping
    @Operation(
            summary = "Create a new post",
            description = "Create a new post (personal or community post). Requires authentication.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Post created successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input or community/topic not found",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - authentication required",
                    content = @Content
            )
    })
    public ResponseEntity<StandardResponse<PostResponse>> createPost(
            @Valid @RequestBody CreatePostRequest request,
            Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardResponse.error("Authentication required"));
        }

        PostResponse response = postService.createPost(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StandardResponse.success("Post created successfully", response));
    }

    @GetMapping("/{publicId}")
    @Operation(
            summary = "Get post by public ID",
            description = "Get post details by public ID. Public endpoint (no authentication required)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Post found",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Post not found",
                    content = @Content
            )
    })
    public ResponseEntity<StandardResponse<PostResponse>> getPost(
            @Parameter(description = "Post public ID", required = true)
            @PathVariable String publicId,
            Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        PostResponse response = postService.getPostByPublicId(publicId, userId);
        return ResponseEntity.ok(StandardResponse.success(response));
    }

    @PutMapping("/{publicId}")
    @Operation(
            summary = "Update post",
            description = "Update post. Only the author can update their own post. Requires authentication.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Post updated successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input or unauthorized",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - authentication required",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Post not found",
                    content = @Content
            )
    })
    public ResponseEntity<StandardResponse<PostResponse>> updatePost(
            @Parameter(description = "Post public ID", required = true)
            @PathVariable String publicId,
            @Valid @RequestBody UpdatePostRequest request,
            Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardResponse.error("Authentication required"));
        }

        PostResponse response = postService.updatePost(publicId, request, userId);
        return ResponseEntity.ok(StandardResponse.success("Post updated successfully", response));
    }

    @DeleteMapping("/{publicId}")
    @Operation(
            summary = "Delete post",
            description = "Delete post. Only the author can delete their own post. Requires authentication.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Post deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Unauthorized - only author can delete",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - authentication required",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Post not found",
                    content = @Content
            )
    })
    public ResponseEntity<StandardResponse<Void>> deletePost(
            @Parameter(description = "Post public ID", required = true)
            @PathVariable String publicId,
            Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardResponse.error("Authentication required"));
        }

        postService.deletePost(publicId, userId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(StandardResponse.success("Post deleted successfully", null));
    }

    @PostMapping("/{publicId}/like")
    @Operation(
            summary = "Like a post",
            description = "Like a post. Requires authentication.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Post liked successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Post already liked or not found",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - authentication required",
                    content = @Content
            )
    })
    public ResponseEntity<StandardResponse<Void>> likePost(
            @Parameter(description = "Post public ID", required = true)
            @PathVariable String publicId,
            Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardResponse.error("Authentication required"));
        }

        postService.likePost(publicId, userId);
        return ResponseEntity.ok(StandardResponse.success("Post liked successfully", null));
    }

    @DeleteMapping("/{publicId}/like")
    @Operation(
            summary = "Unlike a post",
            description = "Unlike a post. Requires authentication.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Post unliked successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Post not liked or not found",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - authentication required",
                    content = @Content
            )
    })
    public ResponseEntity<StandardResponse<Void>> unlikePost(
            @Parameter(description = "Post public ID", required = true)
            @PathVariable String publicId,
            Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardResponse.error("Authentication required"));
        }

        postService.unlikePost(publicId, userId);
        return ResponseEntity.ok(StandardResponse.success("Post unliked successfully", null));
    }

    @GetMapping
    @Operation(
            summary = "Get feed posts",
            description = "Get feed posts (all posts, ordered by creation date). Public endpoint with optional authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Posts retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))
            )
    })
    public ResponseEntity<StandardResponse<Page<PostResponse>>> getFeed(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort direction", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sort,
            Authentication authentication) {
        Sort.Direction direction = sort.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt"));
        
        Long userId = getCurrentUserId(authentication);
        Page<PostResponse> posts = postService.getFeed(pageable, userId);
        return ResponseEntity.ok(StandardResponse.success(posts));
    }

    @GetMapping("/user/{userPublicId}")
    @Operation(
            summary = "Get posts by user",
            description = "Get all posts by a specific user. Public endpoint with optional authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Posts retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found",
                    content = @Content
            )
    })
    public ResponseEntity<StandardResponse<Page<PostResponse>>> getPostsByUser(
            @Parameter(description = "User public ID", required = true)
            @PathVariable String userPublicId,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Sort.Direction direction = Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt"));
        
        Long userId = getCurrentUserId(authentication);
        Page<PostResponse> posts = postService.getPostsByUser(userPublicId, pageable, userId);
        return ResponseEntity.ok(StandardResponse.success(posts));
    }

    @GetMapping("/user/{userPublicId}/personal")
    @Operation(
            summary = "Get personal posts by user",
            description = "Get personal posts (not community posts) by a specific user. Public endpoint with optional authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Posts retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found",
                    content = @Content
            )
    })
    public ResponseEntity<StandardResponse<Page<PostResponse>>> getPersonalPostsByUser(
            @Parameter(description = "User public ID", required = true)
            @PathVariable String userPublicId,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Sort.Direction direction = Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt"));
        
        Long userId = getCurrentUserId(authentication);
        Page<PostResponse> posts = postService.getPersonalPostsByUser(userPublicId, pageable, userId);
        return ResponseEntity.ok(StandardResponse.success(posts));
    }

    @GetMapping("/community/{communityPublicId}")
    @Operation(
            summary = "Get posts by community",
            description = "Get all posts in a specific community. Public endpoint with optional authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Posts retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PostResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Community not found",
                    content = @Content
            )
    })
    public ResponseEntity<StandardResponse<Page<PostResponse>>> getPostsByCommunity(
            @Parameter(description = "Community public ID", required = true)
            @PathVariable String communityPublicId,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Sort.Direction direction = Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt"));
        
        Long userId = getCurrentUserId(authentication);
        Page<PostResponse> posts = postService.getPostsByCommunity(communityPublicId, pageable, userId);
        return ResponseEntity.ok(StandardResponse.success(posts));
    }
}
