package com.longdx.silre_backend.exception;

/**
 * Exception thrown when a user attempts to perform an action they don't have permission for.
 * 
 * This should result in HTTP 403 Forbidden status.
 */
public class ForbiddenException extends RuntimeException {
    
    public ForbiddenException(String message) {
        super(message);
    }
    
    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
