package com.longdx.silre_backend.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh Token Request DTO
 * 
 * Used to refresh access token using refresh token.
 * 
 * Pattern:
 * - Record type for immutability
 * - Validation annotations for input validation
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
    /**
     * Compact constructor with validation
     */
    public RefreshTokenRequest {
        if (refreshToken != null) {
            refreshToken = refreshToken.trim();
        }
    }
}
