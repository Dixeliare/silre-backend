package com.longdx.silre_backend.util;

import com.longdx.silre_backend.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT Token Provider Utility
 * 
 * Handles JWT token generation, validation, and claim extraction.
 * 
 * Pattern:
 * - Stateless JWT tokens (no server-side storage)
 * - HMAC-SHA-256 signing (symmetric key)
 * - Access token (short-lived) + Refresh token (long-lived)
 * - Standard claims: iss, sub, iat, exp, jti
 * - Custom claims: userId, publicId, type (access/refresh)
 * 
 * Security Best Practices:
 * - Access tokens expire quickly (15-30 min) to limit exposure
 * - Refresh tokens expire slowly (7-30 days) for better UX
 * - Tokens are signed and verified to prevent tampering
 * - Token type claim prevents access token reuse as refresh token
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final String issuer;

    public JwtTokenProvider(JwtConfig jwtConfig) {
        this.secretKey = jwtConfig.jwtSecretKey();
        this.accessTokenExpiration = jwtConfig.getAccessTokenExpiration();
        this.refreshTokenExpiration = jwtConfig.getRefreshTokenExpiration();
        this.issuer = jwtConfig.getIssuer();
    }

    /**
     * Generate Access Token
     * 
     * Access tokens are short-lived and used for API authentication.
     * 
     * @param userId Internal user ID (TSID)
     * @param publicId Public user ID (NanoID)
     * @return JWT access token string
     */
    public String generateAccessToken(Long userId, String publicId) {
        return generateToken(userId, publicId, "access", accessTokenExpiration);
    }

    /**
     * Generate Refresh Token
     * 
     * Refresh tokens are long-lived and used to obtain new access tokens.
     * 
     * @param userId Internal user ID (TSID)
     * @param publicId Public user ID (NanoID)
     * @return JWT refresh token string
     */
    public String generateRefreshToken(Long userId, String publicId) {
        return generateToken(userId, publicId, "refresh", refreshTokenExpiration);
    }

    /**
     * Generate JWT Token with custom claims
     * 
     * @param userId Internal user ID (TSID)
     * @param publicId Public user ID (NanoID)
     * @param tokenType Token type: "access" or "refresh"
     * @param expirationMs Expiration time in milliseconds
     * @return JWT token string
     */
    private String generateToken(Long userId, String publicId, String tokenType, long expirationMs) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        // Custom claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString()); // Store as string to avoid precision loss in JSON
        claims.put("publicId", publicId);
        claims.put("type", tokenType); // "access" or "refresh"

        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString()) // Standard claim: subject (user ID)
                .issuedAt(now) // Standard claim: issued at
                .expiration(expiration) // Standard claim: expiration
                .claims(claims) // Custom claims
                .signWith(secretKey) // Sign with HMAC-SHA-256
                .compact();
    }

    /**
     * Validate JWT Token
     * 
     * Verifies token signature, expiration, and issuer.
     * 
     * @param token JWT token string
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract User ID from token
     * 
     * @param token JWT token string
     * @return User ID (TSID) as Long
     */
    public Long getUserIdFromToken(String token) {
        String userIdStr = getClaimFromToken(token, Claims::getSubject);
        return Long.parseLong(userIdStr);
    }

    /**
     * Extract Public ID from token
     * 
     * @param token JWT token string
     * @return Public ID (NanoID) as String
     */
    public String getPublicIdFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("publicId", String.class));
    }

    /**
     * Extract Token Type from token
     * 
     * @param token JWT token string
     * @return Token type: "access" or "refresh"
     */
    public String getTokenTypeFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("type", String.class));
    }

    /**
     * Extract Expiration Date from token
     * 
     * @param token JWT token string
     * @return Expiration date
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * Check if token is expired
     * 
     * @param token JWT token string
     * @return true if token is expired, false otherwise
     */
    public Boolean isTokenExpired(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true; // If we can't parse expiration, consider it expired
        }
    }

    /**
     * Extract claim from token
     * 
     * Generic method to extract any claim from token.
     * 
     * @param token JWT token string
     * @param claimsResolver Function to extract claim
     * @param <T> Claim type
     * @return Claim value
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        try {
            Claims claims = extractAllClaims(token);
            return claimsResolver.apply(claims);
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("Failed to extract claim from token: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid token: " + e.getMessage(), e);
        }
    }

    /**
     * Extract all claims from token
     * 
     * @param token JWT token string
     * @return Claims object
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validate token type
     * 
     * Ensures access tokens are not used as refresh tokens and vice versa.
     * 
     * @param token JWT token string
     * @param expectedType Expected token type: "access" or "refresh"
     * @return true if token type matches expected type
     */
    public boolean validateTokenType(String token, String expectedType) {
        try {
            String tokenType = getTokenTypeFromToken(token);
            return expectedType.equals(tokenType);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get access token expiration time in milliseconds
     * 
     * @return Access token expiration time in milliseconds
     */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
}
