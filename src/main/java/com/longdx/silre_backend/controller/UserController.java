package com.longdx.silre_backend.controller;

import com.longdx.silre_backend.dto.request.CreateUserRequest;
import com.longdx.silre_backend.dto.response.UserResponse;
import com.longdx.silre_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * REST Controller for User operations
 * 
 * Pattern:
 * - @RestController annotation
 * - @RequestMapping for base path
 * - Use DTOs for request/response
 * - @Valid for request validation
 * - Return ResponseEntity for status control
 * - Use proper HTTP status codes
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User management APIs - Create, read, and update user profiles")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Create a new user
     * POST /api/v1/users
     */
    @Operation(
        summary = "Create a new user",
        description = "Register a new user account with email, password, and display name. " +
                     "Returns the created user with generated public ID (NanoID) and internal ID (TSID)."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "User created successfully",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input or email already exists",
            content = @Content
        )
    })
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        try {
            UserResponse user = userService.createUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(user);
        } catch (IllegalArgumentException e) {
            // TODO: Use proper exception handler
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Get user by public ID
     * GET /api/v1/users/{publicId}
     */
    @Operation(
        summary = "Get user by public ID",
        description = "Retrieve user information using public ID (NanoID). " +
                     "Public ID is used in URLs and public APIs instead of internal TSID."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "User found",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content
        )
    })
    @GetMapping("/{publicId}")
    public ResponseEntity<UserResponse> getUserByPublicId(
            @Parameter(description = "User's public ID (NanoID)", required = true, example = "Xy9zQ2mP")
            @PathVariable String publicId) {
        Optional<UserResponse> user = userService.getUserByPublicId(publicId);
        return user.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update user profile
     * PATCH /api/v1/users/{publicId}
     */
    @Operation(
        summary = "Update user profile",
        description = "Update user's display name and/or bio. Both fields are optional."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Profile updated successfully",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content
        )
    })
    @PatchMapping("/{publicId}")
    public ResponseEntity<UserResponse> updateProfile(
            @Parameter(description = "User's public ID (NanoID)", required = true, example = "Xy9zQ2mP")
            @PathVariable String publicId,
            @Parameter(description = "New display name (optional)")
            @RequestParam(required = false) String displayName,
            @Parameter(description = "New bio text (optional)")
            @RequestParam(required = false) String bio) {
        try {
            UserResponse user = userService.updateProfile(publicId, displayName, bio);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Check if email exists
     * GET /api/v1/users/check-email?email={email}
     */
    @Operation(
        summary = "Check if email exists",
        description = "Check if an email address is already registered in the system. " +
                     "Useful for registration form validation."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Email check result",
            content = @Content(schema = @Schema(type = "boolean"))
        )
    })
    @GetMapping("/check-email")
    public ResponseEntity<Boolean> checkEmail(
            @Parameter(description = "Email address to check", required = true, example = "user@example.com")
            @RequestParam String email) {
        boolean exists = userService.emailExists(email);
        return ResponseEntity.ok(exists);
    }
}

