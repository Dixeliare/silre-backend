package com.longdx.silre_backend.config;

import com.github.f4b6a3.tsid.TsidFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ===============================================================================
 * TSID HEALTH INDICATOR - Spring Boot Actuator Integration
 * ===============================================================================
 * 
 * Exposes TSID system status via Spring Boot Actuator health endpoint.
 * Access via: GET /actuator/health
 * 
 * -------------------------------------------------------------------------------
 * WHAT IT PROVIDES
 * -------------------------------------------------------------------------------
 * - TSID factory status (injected or not)
 * - Node ID allocation status
 * - Clock synchronization warnings
 * - Test TSID generation (verifies system works)
 * 
 * -------------------------------------------------------------------------------
 * EXAMPLE HEALTH RESPONSE
 * -------------------------------------------------------------------------------
 * GET /actuator/health
 * 
 * {
 * "status": "UP",
 * "components": {
 * "tsid": {
 * "status": "UP",
 * "details": {
 * "status": "TSID system operational",
 * "nodeId": 0,
 * "testTsid": 1234567890123456789,
 * "timestamp": "2024-01-15T12:30:45Z",
 * "maxNodeId": 1023,
 * "nodeCapacity": "1024 instances"
 * }
 * }
 * }
 * }
 * 
 * -------------------------------------------------------------------------------
 * CLOCK SYNCHRONIZATION CHECK
 * -------------------------------------------------------------------------------
 * TSID relies on system clock for the 42-bit timestamp component.
 * If system clock is out of sync (e.g., NTP not configured):
 * - TSID timestamps won't match actual creation time
 * - In extreme cases, could cause ID collisions
 * 
 * This health indicator warns if clock drift > 1 second is detected.
 * 
 * Note: Spring Boot 4.0 uses org.springframework.boot.health.contributor
 * package
 * (changed from org.springframework.boot.actuate.health in earlier versions)
 * 
 * @see TsidConfig The configuration that creates TsidFactory
 * @see TsidIdGenerator The Hibernate generator
 */
@Component // Auto-registers as a Spring health indicator
public class TsidHealthIndicator implements HealthIndicator {

    // ===============================================================================
    // FIELDS
    // ===============================================================================

    /**
     * Logger for health check messages.
     */
    private static final Logger logger = LoggerFactory.getLogger(TsidHealthIndicator.class);

    /**
     * The TsidFactory instance to check.
     * Injected by Spring (created by TsidConfig).
     */
    private final TsidFactory tsidFactory;

    /**
     * The allocated Node ID (set by TsidConfig after allocation).
     * 
     * Static field to avoid circular dependency with TsidConfig.
     * TsidConfig sets this value after allocating Node ID.
     * AtomicInteger for thread-safe access.
     * Initial value -1 means "not set yet".
     */
    private static final AtomicInteger allocatedNodeId = new AtomicInteger(-1);

    // ===============================================================================
    // CONSTRUCTOR
    // ===============================================================================

    /**
     * Constructor injection of TsidFactory.
     * 
     * @param tsidFactory The factory created by TsidConfig (Spring-managed)
     */
    public TsidHealthIndicator(TsidFactory tsidFactory) {
        this.tsidFactory = tsidFactory;
    }

    // ===============================================================================
    // NODE ID SETTER
    // ===============================================================================

    /**
     * Sets the allocated Node ID for health reporting.
     * 
     * Called by TsidConfig after successful node allocation.
     * Static method to avoid circular dependency.
     * 
     * @param nodeId The Node ID allocated from Redis (0-1023)
     */
    public static void setAllocatedNodeId(int nodeId) {
        allocatedNodeId.set(nodeId);
    }

    // ===============================================================================
    // HEALTH CHECK IMPLEMENTATION
    // ===============================================================================

    /**
     * Performs the TSID system health check.
     * 
     * Called by Spring Actuator when accessing /actuator/health endpoint.
     * 
     * -------------------------------------------------------------------------------
     * CHECKS PERFORMED
     * -------------------------------------------------------------------------------
     * 1. Verify TsidFactory is initialized
     * 2. Test TSID generation (actually create one)
     * 3. Check clock synchronization (compare system time vs TSID time)
     * 4. Verify Node ID is properly allocated
     * 5. Return UP with details, or DOWN with error
     * 
     * @return Health status with details about TSID system
     */
    @Override
    public Health health() {
        try {
            // -------------------------------------------------------------------------------
            // CHECK 1: Is TsidFactory initialized?
            // -------------------------------------------------------------------------------
            if (tsidFactory == null) {
                Map<String, Object> details = new HashMap<>();
                details.put("status", "TSID Factory not initialized");
                details.put("error", "Factory injection failed");

                // Return DOWN status - TSID system is not operational
                return Health.down().withDetails(details).build();
            }

            // -------------------------------------------------------------------------------
            // CHECK 2: Test TSID generation
            // -------------------------------------------------------------------------------
            // Actually create a TSID to verify the factory works
            var testTsid = tsidFactory.create();
            long tsidValue = testTsid.toLong();

            // -------------------------------------------------------------------------------
            // CHECK 3: Clock synchronization
            // -------------------------------------------------------------------------------
            // Compare system time with TSID's embedded timestamp
            // Large difference indicates NTP is not configured properly
            long currentTime = System.currentTimeMillis();
            long tsidTime = extractTimestamp(tsidValue);
            long timeDiff = Math.abs(currentTime - tsidTime);

            // -------------------------------------------------------------------------------
            // BUILD HEALTH DETAILS
            // -------------------------------------------------------------------------------
            Map<String, Object> details = new HashMap<>();
            details.put("status", "TSID system operational");
            details.put("nodeId", allocatedNodeId.get() >= 0 ? allocatedNodeId.get() : "unknown");
            details.put("testTsid", tsidValue);
            details.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
            details.put("maxNodeId", 1023);
            details.put("nodeCapacity", "1024 instances");

            // -------------------------------------------------------------------------------
            // WARNING: Clock drift detected (> 1 second difference)
            // -------------------------------------------------------------------------------
            // This could indicate:
            // - NTP not configured
            // - System clock drifting
            // - VM time sync issues
            if (timeDiff > 1000) {
                String warning = String.format(
                        "Clock synchronization issue detected. Time difference: %d ms. " +
                                "Ensure NTP is configured correctly. TSID uniqueness may be compromised.",
                        timeDiff);
                details.put("warning", warning);
                logger.warn("TSID Health Check: Clock synchronization warning - {} ms difference", timeDiff);
            }

            // -------------------------------------------------------------------------------
            // WARNING: Node ID not properly set
            // -------------------------------------------------------------------------------
            // This could indicate:
            // - Dev mode (random Node ID)
            // - TsidConfig didn't call setAllocatedNodeId()
            if (allocatedNodeId.get() < 0) {
                details.put("warning", "Node ID not properly allocated. TSID uniqueness may be compromised.");
            }

            // -------------------------------------------------------------------------------
            // Return UP status with all details
            // -------------------------------------------------------------------------------
            return Health.up().withDetails(details).build();

        } catch (Exception e) {
            // -------------------------------------------------------------------------------
            // HEALTH CHECK FAILED - Return DOWN status
            // -------------------------------------------------------------------------------
            logger.error("TSID Health Check failed", e);

            Map<String, Object> details = new HashMap<>();
            details.put("status", "TSID system failure");
            details.put("error", e.getMessage());

            return Health.down().withDetails(details).build();
        }
    }

    // ===============================================================================
    // HELPER METHODS
    // ===============================================================================

    /**
     * Extracts the timestamp component from a TSID value.
     * 
     * -------------------------------------------------------------------------------
     * TSID BIT STRUCTURE (64 bits total)
     * -------------------------------------------------------------------------------
     * +------------------------+-----------+---------------------------+
     * | Timestamp (42 bits) | Node (10) | Sequence (12 bits) |
     * | MSB | | LSB |
     * +------------------------+-----------+---------------------------+
     * 
     * To extract timestamp:
     * 1. Shift right by 22 bits (10 + 12) to move timestamp to LSB
     * 2. Mask with 0x3FFFFFFFFFFL (42 ones) to isolate timestamp
     * 
     * Note: This extraction is APPROXIMATE because the tsid-creator library
     * uses a custom epoch (not Unix epoch 1970). For health checking purposes,
     * the relative difference is what matters.
     * 
     * @param tsid The 64-bit TSID value
     * @return The 42-bit timestamp component (approximate)
     */
    private long extractTimestamp(long tsid) {
        // Shift right by 22 bits (10 node bits + 12 sequence bits)
        // Then mask to keep only the 42 timestamp bits
        // 0x3FFFFFFFFFFL = 42 bits of 1s = 0b111111111111111111111111111111111111111111
        return (tsid >> 22) & 0x3FFFFFFFFFFL;
    }
}
