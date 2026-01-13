package com.longdx.silre_backend.service.impl;

import com.longdx.silre_backend.dto.request.CreateUserRequest;
import com.longdx.silre_backend.dto.request.LoginRequest;
import com.longdx.silre_backend.dto.request.RefreshTokenRequest;
import com.longdx.silre_backend.dto.request.RegisterRequest;
import com.longdx.silre_backend.dto.response.AuthResponse;
import com.longdx.silre_backend.dto.response.UserResponse;
import com.longdx.silre_backend.model.User;
import com.longdx.silre_backend.repository.UserRepository;
import com.longdx.silre_backend.service.AuthService;
import com.longdx.silre_backend.service.UserService;
import com.longdx.silre_backend.util.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Authentication Service Implementation
 * 
 * Handles user authentication, registration, and token management.
 * 
 * Pattern:
 * - @Service annotation
 * - @Transactional for write operations
 * - Inject repositories and utilities
 * - Handle business logic
 * - Map entities to DTOs
 * - Throw exceptions for error cases
 */
@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthServiceImpl(
            UserRepository userRepository,
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        logger.debug("Registering new user with email: {}", request.email());

        // Check if email already exists
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists: " + request.email());
        }

        // Create user using UserService (handles NanoID generation, TSID, etc.)
        CreateUserRequest createUserRequest =
                new CreateUserRequest(
                        request.displayName(),
                        request.email(),
                        request.password()
                );
        
        UserResponse userResponse = userService.createUser(createUserRequest);

        // Get user entity for token generation
        User user = userRepository.findByPublicId(userResponse.publicId())
                .orElseThrow(() -> new IllegalStateException("User created but not found: " + userResponse.publicId()));

        // Update last login time
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user.getInternalId(), user.getPublicId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getInternalId(), user.getPublicId());

        // Calculate expiration in seconds
        long expiresInSeconds = jwtTokenProvider.getAccessTokenExpiration() / 1000;

        logger.info("User registered successfully: {} (email: {})", user.getPublicId(), user.getEmail());

        return AuthResponse.of(accessToken, refreshToken, expiresInSeconds, user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        logger.debug("Login attempt for email: {}", request.email());

        // Find user by email
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        // Check if user is active
        if (!user.getIsActive() || !"ACTIVE".equals(user.getAccountStatus())) {
            throw new IllegalArgumentException("Account is not active");
        }

        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            logger.warn("Invalid password attempt for email: {}", request.email());
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Update last login time
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user.getInternalId(), user.getPublicId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getInternalId(), user.getPublicId());

        // Calculate expiration in seconds
        long expiresInSeconds = jwtTokenProvider.getAccessTokenExpiration() / 1000;

        logger.info("User logged in successfully: {} (email: {})", user.getPublicId(), user.getEmail());

        return AuthResponse.of(accessToken, refreshToken, expiresInSeconds, user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        logger.debug("Refreshing access token");

        String refreshToken = request.refreshToken();

        // Validate refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        // Check token type
        if (!jwtTokenProvider.validateTokenType(refreshToken, "refresh")) {
            throw new IllegalArgumentException("Token is not a refresh token");
        }

        // Check if token is expired
        if (jwtTokenProvider.isTokenExpired(refreshToken)) {
            throw new IllegalArgumentException("Refresh token has expired");
        }

        // Extract user info from token
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // Verify user exists and is active
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.getIsActive() || !"ACTIVE".equals(user.getAccountStatus())) {
            throw new IllegalArgumentException("Account is not active");
        }

        // Generate new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getInternalId(), user.getPublicId());

        // Calculate expiration in seconds
        long expiresInSeconds = jwtTokenProvider.getAccessTokenExpiration() / 1000;

        logger.info("Access token refreshed for user: {} (email: {})", user.getPublicId(), user.getEmail());

        // Return new access token with same refresh token
        return AuthResponse.of(newAccessToken, refreshToken, expiresInSeconds, user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateAccessToken(String token) {
        try {
            // Validate token signature and expiration
            if (!jwtTokenProvider.validateToken(token)) {
                return false;
            }

            // Check token type
            if (!jwtTokenProvider.validateTokenType(token, "access")) {
                return false;
            }

            // Check if token is expired
            if (jwtTokenProvider.isTokenExpired(token)) {
                return false;
            }

            // Verify user exists and is active
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            User user = userRepository.findById(userId)
                    .orElse(null);

            if (user == null || !user.getIsActive() || !"ACTIVE".equals(user.getAccountStatus())) {
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.debug("Access token validation failed: {}", e.getMessage());
            return false;
        }
    }
}
