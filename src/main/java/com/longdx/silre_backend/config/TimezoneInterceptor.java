package com.longdx.silre_backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.ZoneId;

/**
 * Interceptor to detect and set user timezone from HTTP request headers.
 * 
 * Detection priority:
 * 1. X-Timezone header (explicit timezone from client)
 * 2. User's stored timezone (if authenticated)
 * 3. Default: UTC
 * 
 * The detected timezone is stored in TimezoneContext for use during request processing.
 */
@Component
public class TimezoneInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(TimezoneInterceptor.class);
    
    /**
     * HTTP header name for explicit timezone (e.g., "X-Timezone: Asia/Ho_Chi_Minh")
     */
    private static final String TIMEZONE_HEADER = "X-Timezone";
    
    /**
     * Alternative header name (some clients use this)
     */
    private static final String TIMEZONE_HEADER_ALT = "Timezone";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Priority 1: Check X-Timezone header (explicit from client)
        String timezoneHeader = request.getHeader(TIMEZONE_HEADER);
        if (timezoneHeader == null || timezoneHeader.trim().isEmpty()) {
            timezoneHeader = request.getHeader(TIMEZONE_HEADER_ALT);
        }
        
        if (timezoneHeader != null && !timezoneHeader.trim().isEmpty()) {
            try {
                ZoneId zoneId = ZoneId.of(timezoneHeader.trim());
                TimezoneContext.setTimezone(zoneId);
                logger.debug("Timezone detected from header: {}", zoneId);
                return true;
            } catch (Exception e) {
                logger.warn("Invalid timezone in header '{}': {}. Using UTC.", timezoneHeader, e.getMessage());
                TimezoneContext.setTimezone(ZoneId.of("UTC"));
                return true;
            }
        }
        
        // Priority 2: TODO - Get from authenticated user's profile
        // This would require:
        // 1. Extract user from SecurityContext
        // 2. Load user entity
        // 3. Get user.getTimezone()
        // For now, we'll implement this later when authentication is added
        
        // Priority 3: Default to UTC
        TimezoneContext.setTimezone(ZoneId.of("UTC"));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) {
        // Clean up thread-local after request completes
        TimezoneContext.clear();
    }
}
