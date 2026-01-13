package com.longdx.silre_backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login Request DTO
 * 
 * Used for user authentication.
 * 
 * Pattern:
 * - Record type for immutability
 * - Validation annotations for input validation
 * - Compact constructor for validation
 */
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
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
    public LoginRequest {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
        if (password != null) {
            password = password.trim();
        }
    }
}
