package com.longdx.silre_backend.exception;

import com.longdx.silre_backend.dto.response.StandardResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler
 * 
 * Handles exceptions thrown by controllers and services.
 * Returns consistent error responses.
 * 
 * Pattern:
 * - @RestControllerAdvice to handle exceptions globally
 * - @ExceptionHandler for specific exception types
 * - Return ResponseEntity with proper HTTP status codes
 * - Consistent error response format
 * 
 * Note: @Hidden annotation excludes this class from SpringDoc OpenAPI scanning
 * to avoid compatibility issues with Spring Boot 4.0.1
 */
@RestControllerAdvice
@Hidden  // Exclude from SpringDoc OpenAPI scanning (fixes compatibility with Spring Boot 4.0.1)
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Helper method to create error response entity
     * Used by .cursorrules pattern for consistent error responses
     */
    public static ResponseEntity<StandardResponse<?>> errorResponseEntity(String message, HttpStatus status) {
        String errorCode = mapHttpStatusToErrorCode(status);
        StandardResponse<?> response = StandardResponse.error(errorCode, message);
        return new ResponseEntity<>(response, status);
    }

    /**
     * Map HTTP status to error code
     */
    private static String mapHttpStatusToErrorCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case INTERNAL_SERVER_ERROR -> "INTERNAL_SERVER_ERROR";
            default -> "GENERIC_ERROR";
        };
    }

    /**
     * Handle validation errors (from @Valid annotations)
     * Returns detailed field-level validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        StandardResponse<Map<String, String>> response = StandardResponse.validationError(
            "Validation failed",
            errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle IllegalArgumentException (business logic errors)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<StandardResponse<?>> handleIllegalArgumentException(
            IllegalArgumentException ex) {
        logger.debug("IllegalArgumentException: {}", ex.getMessage());
        StandardResponse<?> response = StandardResponse.error("INVALID_ARGUMENT", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle AuthenticationException (security errors)
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<StandardResponse<?>> handleAuthenticationException(
            AuthenticationException ex) {
        logger.debug("AuthenticationException: {}", ex.getMessage());
        StandardResponse<?> response = StandardResponse.error("AUTHENTICATION_FAILED", 
            "Authentication failed: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle BadCredentialsException (invalid credentials)
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<StandardResponse<?>> handleBadCredentialsException(
            BadCredentialsException ex) {
        logger.debug("BadCredentialsException: {}", ex.getMessage());
        StandardResponse<?> response = StandardResponse.error("INVALID_CREDENTIALS", "Invalid credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle ForbiddenException (authorization errors)
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<StandardResponse<?>> handleForbiddenException(
            ForbiddenException ex) {
        logger.debug("ForbiddenException: {}", ex.getMessage());
        StandardResponse<?> response = StandardResponse.error("FORBIDDEN", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handle generic exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardResponse<?>> handleGenericException(Exception ex) {
        logger.error("Unexpected error: ", ex);
        StandardResponse<?> response = StandardResponse.error("INTERNAL_SERVER_ERROR", 
            "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
