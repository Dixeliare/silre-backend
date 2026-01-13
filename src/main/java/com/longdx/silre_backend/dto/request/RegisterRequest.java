package com.longdx.silre_backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Register Request DTO
 * 
 * Used for user registration.
 * 
 * Pattern:
 * - Record type for immutability
 * - Validation annotations for input validation
 * - Compact constructor for validation
 */
public record RegisterRequest(
        @NotBlank(message = "Display name is required")
        @Size(min = 1, max = 255, message = "Display name must be between 1 and 255 characters")
        String displayName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password
) {
    /**
     * Compact constructor with validation
     * 
     * Validates input parameters before creating record instance.
     */
    public RegisterRequest {
        if (displayName != null) {
            displayName = displayName.trim();
        }
        if (email != null) {
            email = email.trim().toLowerCase();
        }
        if (password != null) {
            password = password.trim();
        }
    }
}
