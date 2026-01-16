package com.longdx.silre_backend.controller;

import com.longdx.silre_backend.dto.request.LoginRequest;
import com.longdx.silre_backend.dto.request.RefreshTokenRequest;
import com.longdx.silre_backend.dto.request.RegisterRequest;
import com.longdx.silre_backend.dto.response.AuthResponse;
import com.longdx.silre_backend.dto.response.StandardResponse;
import com.longdx.silre_backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller
 * 
 * Handles user authentication endpoints: register, login, refresh token.
 * 
 * Pattern:
 * - @RestController annotation
 * - @RequestMapping for base path
 * - Use DTOs for request/response
 * - @Valid for request validation
 * - Return ResponseEntity for status control
 * - Use proper HTTP status codes
 * - All endpoints are public (no authentication required)
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User authentication APIs - Register, login, and refresh tokens")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Register a new user
     * POST /api/v1/auth/register
     */
    @Operation(
            summary = "Register a new user",
            description = "Create a new user account with display name, email, and password. " +
                    "Returns authentication tokens (access token and refresh token) along with user information."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input or email already exists",
                    content = @Content
            )
    })
    @PostMapping("/register")
    public ResponseEntity<StandardResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(StandardResponse.success("User registered successfully", response));
    }

    /**
     * Login user
     * POST /api/v1/auth/login
     */
    @Operation(
            summary = "Login user",
            description = "Authenticate user with email and password. " +
                    "Returns authentication tokens (access token and refresh token) along with user information."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials or account not active",
                    content = @Content
            )
    })
    @PostMapping("/login")
    public ResponseEntity<StandardResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(StandardResponse.success("Login successful", response));
    }

    /**
     * Refresh access token
     * POST /api/v1/auth/refresh
     */
    @Operation(
            summary = "Refresh access token",
            description = "Generate a new access token using a valid refresh token. " +
                    "Returns new access token with same refresh token and user information."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid or expired refresh token",
                    content = @Content
            )
    })
    @PostMapping("/refresh")
    public ResponseEntity<StandardResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(StandardResponse.success("Token refreshed successfully", response));
    }

    /**
     * Validate access token
     * GET /api/v1/auth/validate
     * 
     * This endpoint can be used by frontend to check if token is still valid.
     */
    @Operation(
            summary = "Validate access token",
            description = "Check if the provided access token is valid and not expired. " +
                    "Token should be provided in Authorization header as 'Bearer <token>' or as query parameter 'token'.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Token is valid",
                    content = @Content(schema = @Schema(type = "boolean"))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Token is invalid or expired",
                    content = @Content
            )
    })
    @GetMapping("/validate")
    public ResponseEntity<StandardResponse<Boolean>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {
        
        String token = null;
        
        // Try to get token from Authorization header first
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7); // Remove "Bearer " prefix
        } 
        // Fallback: try to get token from query parameter (for easier testing)
        else if (tokenParam != null && !tokenParam.isEmpty()) {
            token = tokenParam;
        }
        
        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardResponse.error("Token is required"));
        }

        boolean isValid = authService.validateAccessToken(token);
        
        if (isValid) {
            return ResponseEntity.ok(StandardResponse.success("Token is valid", true));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardResponse.error("Token is invalid or expired"));
        }
    }
}
