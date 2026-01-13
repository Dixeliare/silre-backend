package com.longdx.silre_backend.dto.response;

import com.longdx.silre_backend.model.User;

/**
 * Authentication Response DTO
 * 
 * Contains access token, refresh token, and user information.
 * 
 * Pattern:
 * - Record type for immutability
 * - Static factory method from entity
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        UserResponse user
) {
    /**
     * Token type constant
     */
    public static final String TOKEN_TYPE_BEARER = "Bearer";

    /**
     * Create AuthResponse from User entity and tokens
     * 
     * @param accessToken JWT access token
     * @param refreshToken JWT refresh token
     * @param expiresIn Access token expiration time in seconds
     * @param user User entity
     * @return AuthResponse instance
     */
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, User user) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                TOKEN_TYPE_BEARER,
                expiresIn,
                UserResponse.from(user)
        );
    }
}
