package com.longdx.silre_backend.service;

import com.longdx.silre_backend.dto.request.LoginRequest;
import com.longdx.silre_backend.dto.request.RefreshTokenRequest;
import com.longdx.silre_backend.dto.request.RegisterRequest;
import com.longdx.silre_backend.dto.response.AuthResponse;

/**
 * Authentication Service Interface
 * 
 * Handles user authentication and authorization.
 * 
 * Pattern:
 * - Interface for service layer
 * - Methods return DTOs, not entities
 * - Throws exceptions for error cases
 */
public interface AuthService {

    /**
     * Register a new user
     * 
     * Creates a new user account and returns authentication tokens.
     * 
     * @param request Registration request with display name, email, and password
     * @return AuthResponse with access token, refresh token, and user info
     * @throws IllegalArgumentException if email already exists
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticate user and generate tokens
     * 
     * Validates credentials and returns authentication tokens.
     * 
     * @param request Login request with email and password
     * @return AuthResponse with access token, refresh token, and user info
     * @throws IllegalArgumentException if credentials are invalid
     */
    AuthResponse login(LoginRequest request);

    /**
     * Refresh access token using refresh token
     * 
     * Validates refresh token and generates new access token.
     * 
     * @param request Refresh token request
     * @return AuthResponse with new access token, same refresh token, and user info
     * @throws IllegalArgumentException if refresh token is invalid or expired
     */
    AuthResponse refreshToken(RefreshTokenRequest request);

    /**
     * Validate access token
     * 
     * Checks if access token is valid and not expired.
     * 
     * @param token JWT access token
     * @return true if token is valid, false otherwise
     */
    boolean validateAccessToken(String token);
}
