package com.longdx.silre_backend.config;

import java.time.ZoneId;

/**
 * Thread-local context for storing user timezone during request processing.
 * 
 * This allows timezone to be accessed from anywhere in the request processing chain
 * without passing it as a parameter through every method call.
 * 
 * Usage:
 * 1. Interceptor extracts timezone from request header
 * 2. Sets it in TimezoneContext
 * 3. Entities/services can access it via TimezoneContext.getCurrentTimezone()
 * 4. Automatically cleared after request completes
 */
public class TimezoneContext {
    
    private static final ThreadLocal<ZoneId> TIMEZONE_HOLDER = new ThreadLocal<>();
    private static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("UTC");
    
    /**
     * Sets the timezone for the current thread (request).
     * 
     * @param zoneId The timezone to set (e.g., "Asia/Ho_Chi_Minh", "America/New_York")
     */
    public static void setTimezone(ZoneId zoneId) {
        if (zoneId != null) {
            TIMEZONE_HOLDER.set(zoneId);
        } else {
            TIMEZONE_HOLDER.set(DEFAULT_TIMEZONE);
        }
    }
    
    /**
     * Sets the timezone from a string (e.g., from HTTP header).
     * 
     * @param timezoneString Timezone string (e.g., "Asia/Ho_Chi_Minh", "UTC", "GMT+7")
     * @throws IllegalArgumentException if timezone string is invalid
     */
    public static void setTimezone(String timezoneString) {
        if (timezoneString == null || timezoneString.trim().isEmpty()) {
            TIMEZONE_HOLDER.set(DEFAULT_TIMEZONE);
            return;
        }
        
        try {
            ZoneId zoneId = ZoneId.of(timezoneString.trim());
            TIMEZONE_HOLDER.set(zoneId);
        } catch (Exception e) {
            // Invalid timezone - fallback to UTC
            TIMEZONE_HOLDER.set(DEFAULT_TIMEZONE);
        }
    }
    
    /**
     * Gets the current timezone for this thread (request).
     * 
     * @return The timezone, or UTC if not set
     */
    public static ZoneId getCurrentTimezone() {
        ZoneId zoneId = TIMEZONE_HOLDER.get();
        return zoneId != null ? zoneId : DEFAULT_TIMEZONE;
    }
    
    /**
     * Clears the timezone for the current thread.
     * Should be called after request processing completes.
     */
    public static void clear() {
        TIMEZONE_HOLDER.remove();
    }
}
