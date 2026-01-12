package com.longdx.silre_backend.config;

import com.github.f4b6a3.tsid.TsidFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
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
     * DataSource for database health check.
     */
    @Autowired(required = false)
    private DataSource dataSource;

    /**
     * Redis template for Redis health check.
     */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

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
     * Performs comprehensive health check for TSID system, Database, and Redis.
     * 
     * Called by Spring Actuator when accessing /actuator/health endpoint.
     * 
     * -------------------------------------------------------------------------------
     * TSID HEALTH CHECKS
     * -------------------------------------------------------------------------------
     * 1. Verify TsidFactory is initialized (Node ID đã được allocate chưa?)
     * 2. Test TSID generation (Factory có generate TSID được không?)
     * 3. Check clock synchronization (System clock có sync không? NTP configured?)
     * 4. Verify Node ID is properly allocated (Node ID có trong range 0-1023 không?)
     * 
     * -------------------------------------------------------------------------------
     * DATABASE HEALTH CHECK
     * -------------------------------------------------------------------------------
     * - Test database connection (có connect được không?)
     * - Verify connection is valid (connection có hoạt động không?)
     * 
     * -------------------------------------------------------------------------------
     * REDIS HEALTH CHECK
     * -------------------------------------------------------------------------------
     * - Test Redis connection (có connect được không?)
     * - Verify Redis is responsive (Redis có respond không?)
     * 
     * @return Health status with details about TSID, Database, and Redis
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
            // CHECK 4: Database connection health
            // -------------------------------------------------------------------------------
            Map<String, Object> dbDetails = new HashMap<>();
            boolean dbHealthy = false;
            if (dataSource != null) {
                try (Connection connection = dataSource.getConnection()) {
                    if (connection.isValid(2)) { // 2 seconds timeout
                        dbHealthy = true;
                        dbDetails.put("status", "Connected");
                        dbDetails.put("database", connection.getMetaData().getDatabaseProductName());
                        dbDetails.put("version", connection.getMetaData().getDatabaseProductVersion());
                    } else {
                        dbDetails.put("status", "Connection invalid");
                        dbDetails.put("error", "Connection validation failed");
                    }
                } catch (SQLException e) {
                    dbDetails.put("status", "Connection failed");
                    dbDetails.put("error", e.getMessage());
                }
            } else {
                dbDetails.put("status", "DataSource not configured");
            }

            // -------------------------------------------------------------------------------
            // CHECK 5: Redis connection health
            // -------------------------------------------------------------------------------
            Map<String, Object> redisDetails = new HashMap<>();
            boolean redisHealthy = false;
            if (redisTemplate != null) {
                try {
                    // Test Redis connection by doing a simple operation
                    String testKey = "health:check:" + System.currentTimeMillis();
                    redisTemplate.opsForValue().set(testKey, "test", java.time.Duration.ofSeconds(1));
                    redisTemplate.delete(testKey);
                    redisHealthy = true;
                    redisDetails.put("status", "Connected");
                    redisDetails.put("host", redisTemplate.getConnectionFactory().getConnection().getNativeConnection().toString().contains("localhost") ? "localhost" : "remote");
                } catch (Exception e) {
                    redisDetails.put("status", "Connection failed");
                    redisDetails.put("error", e.getMessage());
                }
            } else {
                redisDetails.put("status", "Redis not configured");
            }

            // -------------------------------------------------------------------------------
            // BUILD HEALTH DETAILS
            // -------------------------------------------------------------------------------
            Map<String, Object> details = new HashMap<>();
            
            // TSID details
            Map<String, Object> tsidDetails = new HashMap<>();
            tsidDetails.put("status", "TSID system operational");
            tsidDetails.put("nodeId", allocatedNodeId.get() >= 0 ? allocatedNodeId.get() : "unknown");
            tsidDetails.put("testTsid", tsidValue);
            tsidDetails.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
            tsidDetails.put("maxNodeId", 1023);
            tsidDetails.put("nodeCapacity", "1024 instances");
            
            details.put("tsid", tsidDetails);
            details.put("database", dbDetails);
            details.put("redis", redisDetails);

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
                tsidDetails.put("warning", warning);
                logger.warn("TSID Health Check: Clock synchronization warning - {} ms difference", timeDiff);
            }

            // -------------------------------------------------------------------------------
            // WARNING: Node ID not properly set
            // -------------------------------------------------------------------------------
            // This could indicate:
            // - Dev mode (random Node ID)
            // - TsidConfig didn't call setAllocatedNodeId()
            if (allocatedNodeId.get() < 0) {
                tsidDetails.put("warning", "Node ID not properly allocated. TSID uniqueness may be compromised.");
            }

            // -------------------------------------------------------------------------------
            // Determine overall health status
            // -------------------------------------------------------------------------------
            // DOWN if any critical component is down
            boolean allHealthy = dbHealthy && redisHealthy && tsidFactory != null;
            
            if (!allHealthy) {
                return Health.down().withDetails(details).build();
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
