package com.longdx.silre_backend.config;

import com.longdx.silre_backend.util.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT Authentication Filter
 * 
 * Intercepts HTTP requests and validates JWT tokens.
 * Sets authentication in SecurityContext if token is valid.
 * 
 * Pattern:
 * - Extends OncePerRequestFilter (executes once per request)
 * - Extracts JWT token from Authorization header
 * - Validates token and sets authentication
 * - Continues filter chain if token is valid or missing
 * 
 * Security Best Practices:
 * - Only processes requests with Authorization header
 * - Validates token signature, expiration, and type
 * - Sets authentication only if token is valid
 * - Does not throw exceptions (allows other filters to handle)
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // Extract token from Authorization header
            String token = extractTokenFromRequest(request);

            if (token != null && jwtTokenProvider.validateToken(token)) {
                // Validate token type (must be access token)
                if (jwtTokenProvider.validateTokenType(token, "access")) {
                    // Extract user info from token
                    Long userId = jwtTokenProvider.getUserIdFromToken(token);
                    String publicId = jwtTokenProvider.getPublicIdFromToken(token);

                    // Create authentication object
                    Authentication authentication = createAuthentication(userId, publicId, request);

                    // Set authentication in SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    logger.debug("JWT token validated for user: {} (userId: {})", publicId, userId);
                } else {
                    logger.debug("Invalid token type - expected access token");
                }
            } else {
                logger.debug("No valid JWT token found in request");
            }
        } catch (Exception e) {
            logger.error("Error processing JWT token: {}", e.getMessage());
            // Don't set authentication - let other filters handle
            // This allows unauthenticated requests to continue (for public endpoints)
        }

        // Continue filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header
     * 
     * Format: "Bearer <token>"
     * 
     * @param request HTTP request
     * @return JWT token string or null if not found
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    /**
     * Create Authentication object from token claims
     * 
     * @param userId User ID (TSID)
     * @param publicId Public ID (NanoID)
     * @param request HTTP request
     * @return Authentication object
     */
    private Authentication createAuthentication(Long userId, String publicId, HttpServletRequest request) {
        // Create principal (user identifier)
        // Using userId as principal (can be changed to UserDetails if needed)
        String principal = userId.toString();

        // Create authorities (roles/permissions)
        // For now, all authenticated users have USER role
        // Can be extended to support role-based access control
        var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

        // Create authentication token
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);

        // Set request details
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        return authentication;
    }
}
