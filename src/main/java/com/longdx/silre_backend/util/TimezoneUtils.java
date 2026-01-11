package com.longdx.silre_backend.util;

import com.longdx.silre_backend.config.TimezoneContext;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Utility class for timezone-aware timestamp creation.
 * 
 * Use this when creating timestamps in entities or services to ensure
 * they respect the user's timezone preference.
 */
public class TimezoneUtils {

    /**
     * Creates an OffsetDateTime using the detected timezone from request context.
     * 
     * Priority:
     * 1. Timezone from request header (X-Timezone)
     * 2. User's stored timezone (if available)
     * 3. UTC (default)
     * 
     * @return OffsetDateTime with appropriate timezone offset
     */
    public static OffsetDateTime now() {
        ZoneId zoneId = TimezoneContext.getCurrentTimezone();
        return OffsetDateTime.now(zoneId);
    }
    
    /**
     * Creates an OffsetDateTime using a specific timezone.
     * 
     * @param timezoneString Timezone string (e.g., "Asia/Ho_Chi_Minh", "UTC")
     * @return OffsetDateTime with specified timezone
     * @throws IllegalArgumentException if timezone string is invalid
     */
    public static OffsetDateTime now(String timezoneString) {
        ZoneId zoneId;
        if (timezoneString == null || timezoneString.trim().isEmpty()) {
            zoneId = TimezoneContext.getCurrentTimezone();
        } else {
            try {
                zoneId = ZoneId.of(timezoneString);
            } catch (Exception e) {
                // Fallback to detected timezone or UTC
                zoneId = TimezoneContext.getCurrentTimezone();
            }
        }
        return OffsetDateTime.now(zoneId);
    }
    
    /**
     * Creates an OffsetDateTime using a ZoneId.
     * 
     * @param zoneId The timezone to use
     * @return OffsetDateTime with specified timezone
     */
    public static OffsetDateTime now(ZoneId zoneId) {
        if (zoneId == null) {
            zoneId = TimezoneContext.getCurrentTimezone();
        }
        return OffsetDateTime.now(zoneId);
    }
}
