package com.longdx.silre_backend.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT Configuration
 * 
 * Provides JWT-related beans and configuration.
 * 
 * Pattern:
 * - Externalize JWT secret and expiration times via application.yaml
 * - Generate SecretKey from configured secret string
 * - Use HMAC-SHA-256 for signing (symmetric key)
 */
@Configuration
public class JwtConfig {

    /**
     * JWT Secret Key (Base64 encoded or plain string)
     * 
     * IMPORTANT: In production, use environment variable or secret management service.
     * Minimum recommended length: 256 bits (32 characters) for HS256.
     * 
     * Example: ${JWT_SECRET:your-256-bit-secret-key-here-change-in-production}
     */
    @Value("${jwt.secret:your-256-bit-secret-key-here-change-in-production-minimum-32-chars}")
    private String jwtSecret;

    /**
     * Access Token expiration time in milliseconds
     * 
     * Default: 15 minutes (900000 ms)
     * Recommended: 15-30 minutes for access tokens
     */
    @Value("${jwt.access-token-expiration:900000}") // 15 minutes
    private long accessTokenExpiration;

    /**
     * Refresh Token expiration time in milliseconds
     * 
     * Default: 7 days (604800000 ms)
     * Recommended: 7-30 days for refresh tokens
     */
    @Value("${jwt.refresh-token-expiration:604800000}") // 7 days
    private long refreshTokenExpiration;

    /**
     * JWT Issuer claim
     * 
     * Identifies the principal that issued the JWT.
     */
    @Value("${jwt.issuer:silre-backend}")
    private String issuer;

    /**
     * Creates a SecretKey from the configured JWT secret string.
     * 
     * Uses HMAC-SHA-256 algorithm (symmetric key).
     * 
     * @return SecretKey for JWT signing and verification
     */
    @Bean
    public SecretKey jwtSecretKey() {
        // Convert secret string to bytes and create SecretKey
        // If secret is Base64 encoded, decode it first
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        
        // Ensure minimum key size for HS256 (256 bits = 32 bytes)
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                "JWT secret must be at least 32 characters (256 bits) for HS256 algorithm. " +
                "Current length: " + keyBytes.length + " characters. " +
                "Please set jwt.secret in application.yaml or environment variable."
            );
        }
        
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Getter for access token expiration time
     */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    /**
     * Getter for refresh token expiration time
     */
    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    /**
     * Getter for JWT issuer
     */
    public String getIssuer() {
        return issuer;
    }
}
