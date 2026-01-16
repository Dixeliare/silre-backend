package com.longdx.silre_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Standard API Response wrapper
 * 
 * Enterprise-grade API response format with:
 * - Consistent structure across all endpoints
 * - Timestamp for tracking
 * - Error codes for programmatic handling
 * - Detailed validation errors
 * 
 * Pattern:
 * - Success responses: HTTP 2xx with result="SUCCESS"
 * - Error responses: HTTP 4xx/5xx with result="ERROR" and errorCode
 * - Always use proper HTTP status codes (don't wrap everything in 200)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Exclude null fields from JSON
public class StandardResponse<T> {
    private String result;           // SUCCESS or ERROR
    private String message;          // Human-readable message
    private T data;                  // Response data (null for errors)
    private String errorCode;        // Machine-readable error code (for errors)
    private OffsetDateTime timestamp; // Response timestamp
    private Object errors;           // Detailed validation errors (field-level)

    /**
     * Create success response with data
     */
    public static <T> StandardResponse<T> success(T data) {
        return new StandardResponse<>(
            "SUCCESS",
            "Operation completed successfully",
            data,
            null,
            OffsetDateTime.now(),
            null
        );
    }

    /**
     * Create success response with custom message
     */
    public static <T> StandardResponse<T> success(String message, T data) {
        return new StandardResponse<>(
            "SUCCESS",
            message,
            data,
            null,
            OffsetDateTime.now(),
            null
        );
    }

    /**
     * Create error response with error code
     */
    public static <T> StandardResponse<T> error(String errorCode, String message) {
        return new StandardResponse<>(
            "ERROR",
            message,
            null,
            errorCode,
            OffsetDateTime.now(),
            null
        );
    }

    /**
     * Create error response (backward compatibility)
     */
    public static <T> StandardResponse<T> error(String message) {
        return error("GENERIC_ERROR", message);
    }

    /**
     * Create validation error response with field-level details
     */
    public static <T> StandardResponse<T> validationError(String message, Object fieldErrors) {
        return new StandardResponse<>(
            "ERROR",
            message,
            null,
            "VALIDATION_ERROR",
            OffsetDateTime.now(),
            fieldErrors
        );
    }
}
