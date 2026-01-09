package com.longdx.silre_backend.config;

import com.github.f4b6a3.tsid.TsidFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * TSID CONFIGURATION - Enterprise-Grade Distributed ID Generation
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This is the MAIN configuration class for TSID (Time-Sorted Unique
 * Identifier).
 * It handles:
 * - Distributed Node ID allocation via Redis
 * - TsidFactory creation and injection
 * - Scheduled lock refresh (keep-alive)
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │ ENTERPRISE-GRADE FEATURES │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ ✅ Fail-fast node allocation (no silent fallback in production) │
 * │ ✅ Spring-managed keep-alive scheduling (@Scheduled) │
 * │ ✅ Strict factory injection validation │
 * │ ✅ Dev mode for local development without Redis │
 * │ ✅ Automatic lock expiration (self-healing if app crashes) │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │ ARCHITECTURE OVERVIEW │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ │
 * │ ┌─────────────────────────────────────────────────────────────────────┐ │
 * │ │ Application Startup │ │
 * │ └─────────────────────────────────────────────────────────────────────┘ │
 * │ │ │
 * │ ▼ │
 * │ ┌─────────────────────────────────────────────────────────────────────┐ │
 * │ │ TsidConfig.tsidFactory() │ │
 * │ │ 1. Allocates Node ID from Redis (fail-fast if unavailable) │ │
 * │ │ 2. Creates TsidFactory with Node ID │ │
 * │ │ 3. Injects factory into TsidIdGenerator │ │
 * │ │ 4. Registers scheduled lock refresh task │ │
 * │ └─────────────────────────────────────────────────────────────────────┘ │
 * │ │ │
 * │ ▼ │
 * │ ┌─────────────────────────────────────────────────────────────────────┐ │
 * │ │ Entity with @TsidGenerator │ │
 * │ │ → Hibernate calls TsidIdGenerator.generate() │ │
 * │ │ → Returns unique TSID (Long) │ │
 * │ └─────────────────────────────────────────────────────────────────────┘ │
 * │ │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │ NODE ID ALLOCATION - Multi-Instance Scenario │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ │
 * │ Instance 1 starts: │
 * │ → Tries "sys:tsid:node:0" → Success → Uses Node ID 0 │
 * │ │
 * │ Instance 2 starts (5 seconds later): │
 * │ → Tries "sys:tsid:node:0" → Exists (locked) → Tries next │
 * │ → Tries "sys:tsid:node:1" → Success → Uses Node ID 1 │
 * │ │
 * │ Instance 3 starts: │
 * │ → Tries "sys:tsid:node:0" → Exists │
 * │ → Tries "sys:tsid:node:1" → Exists │
 * │ → Tries "sys:tsid:node:2" → Success → Uses Node ID 2 │
 * │ │
 * │ Result: Each instance has UNIQUE Node ID → No ID collisions! │
 * │ │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * 
 * @see TsidGenerator The annotation used on entity @Id fields
 * @see TsidIdGenerator The Hibernate generator that uses TsidFactory
 * @see TsidHealthIndicator Health monitoring via Spring Actuator
 */
@Configuration // Marks this as a Spring configuration class
@EnableScheduling // Enables @Scheduled annotation support for lock refresh
public class TsidConfig {

    // ═══════════════════════════════════════════════════════════════════════════
    // FIELDS & CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(TsidConfig.class);

    /**
     * Redis client for distributed locking.
     * 
     * IMPORTANT: marked as "required = false" to allow dev mode without Redis.
     * In production, Redis is REQUIRED and app will fail-fast if unavailable.
     */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * Development mode flag.
     * 
     * When true: Allows random Node ID if Redis unavailable (local development)
     * When false: Fails fast if Redis unavailable (production safety)
     * 
     * Configure in application.yaml:
     * tsid:
     * allow-dev-mode: true # Only for local development!
     */
    @Value("${tsid.allow-dev-mode:false}")
    private boolean allowDevMode;

    /**
     * Redis key prefix for Node ID allocation.
     * 
     * Keys look like: sys:tsid:node:0, sys:tsid:node:1, ... sys:tsid:node:1023
     */
    private static final String NODE_KEY_PREFIX = "sys:tsid:node:";

    /**
     * Maximum Node ID (exclusive).
     * 
     * TSID uses 10 bits for Node ID → 2^10 = 1024 possible values (0-1023)
     * This means you can run up to 1024 backend instances concurrently.
     */
    private static final int MAX_NODE_ID = 1024;

    /**
     * Stores the allocated Redis key for scheduled lock refresh.
     * 
     * AtomicReference ensures thread-safe access from both main thread
     * and scheduled refresh task.
     * 
     * Example value: "sys:tsid:node:0"
     */
    private final AtomicReference<String> allocatedNodeKey = new AtomicReference<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN BEAN - TsidFactory
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates and configures the TsidFactory bean.
     * 
     * This method runs during Spring Boot startup and:
     * 1. Allocates a unique Node ID from Redis (or random in dev mode)
     * 2. Validates the Node ID is in valid range (0-1023)
     * 3. Creates a TsidFactory with the allocated Node ID
     * 4. Injects the factory into TsidIdGenerator for Hibernate use
     * 5. Returns the factory as a Spring bean
     * 
     * ┌─────────────────────────────────────────────────────────────────────────┐
     * │ WHEN IT RUNS │
     * ├─────────────────────────────────────────────────────────────────────────┤
     * │ • During Spring Boot application startup │
     * │ • BEFORE any entities are created │
     * │ • Only ONCE per application instance │
     * └─────────────────────────────────────────────────────────────────────────┘
     * 
     * @return Configured TsidFactory instance
     * @throws IllegalStateException if Node ID allocation fails (fail-fast)
     */
    @Bean
    public TsidFactory tsidFactory() {

        // ─────────────────────────────────────────────────────────────────────
        // STEP 1: Allocate unique Node ID from Redis
        // ─────────────────────────────────────────────────────────────────────
        // This call attempts to lock a unique Node ID slot in Redis.
        // In production, it fails fast if Redis is unavailable.
        int nodeId = allocateNodeId();

        // ─────────────────────────────────────────────────────────────────────
        // STEP 2: Validate Node ID is in valid range (0-1023)
        // ─────────────────────────────────────────────────────────────────────
        // TSID uses 10 bits for Node ID, so valid range is 0 to 1023.
        // This is a sanity check - should never fail if allocateNodeId() is correct.
        if (nodeId < 0 || nodeId >= MAX_NODE_ID) {
            throw new IllegalStateException(
                    String.format("Invalid TSID Node ID: %d. Must be in range [0, %d)",
                            nodeId, MAX_NODE_ID - 1));
        }

        // ─────────────────────────────────────────────────────────────────────
        // STEP 3: Log initialization info
        // ─────────────────────────────────────────────────────────────────────
        logger.info(">>> TSID Initialized with Node ID: {} (Range: 0-{})", nodeId, MAX_NODE_ID - 1);

        // Clock synchronization warning
        // TSID relies on system clock - drift can cause issues
        logger.info(">>> TSID Clock Synchronization: Ensure NTP is configured. " +
                "Clock drift > 1s may cause ID collisions.");

        // ─────────────────────────────────────────────────────────────────────
        // STEP 4: Create TsidFactory with allocated Node ID
        // ─────────────────────────────────────────────────────────────────────
        // TsidFactory.builder() creates a builder pattern
        // .withNode(nodeId) sets the 10-bit Node ID component
        // .build() creates the immutable factory instance
        TsidFactory factory = TsidFactory.builder()
                .withNode(nodeId)
                .build();

        // ─────────────────────────────────────────────────────────────────────
        // STEP 5: Inject factory into TsidIdGenerator
        // ─────────────────────────────────────────────────────────────────────
        // This static method call connects our factory to Hibernate's generator.
        // All entity saves will now use this factory for ID generation.
        TsidIdGenerator.setTsidFactory(factory);

        // ─────────────────────────────────────────────────────────────────────
        // STEP 6: Return factory as Spring bean
        // ─────────────────────────────────────────────────────────────────────
        // The factory is now available for injection in other components
        // (e.g., TsidHealthIndicator uses it for health checks)
        return factory;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NODE ID ALLOCATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Allocates a unique Node ID from Redis using distributed locking.
     * 
     * ┌─────────────────────────────────────────────────────────────────────────┐
     * │ ALGORITHM │
     * ├─────────────────────────────────────────────────────────────────────────┤
     * │ 1. Check if Redis is available │
     * │ 2. If Redis unavailable: │
     * │ - Dev mode: Use random Node ID (with warning) │
     * │ - Production: FAIL-FAST (throw exception) │
     * │ 3. Loop through Node IDs 0 to 1023: │
     * │ - Try to acquire lock: SETNX "sys:tsid:node:0" "LOCKED" 24h │
     * │ - If acquired (true): Store key for refresh, return Node ID │
     * │ - If not acquired (false): Try next Node ID │
     * │ 4. If all 1024 slots taken: FAIL-FAST (max capacity reached) │
     * └─────────────────────────────────────────────────────────────────────────┘
     * 
     * ┌─────────────────────────────────────────────────────────────────────────┐
     * │ WHY FAIL-FAST? │
     * ├─────────────────────────────────────────────────────────────────────────┤
     * │ • Prevents silent ID collisions in production │
     * │ • If 2 instances accidentally use same Node ID → duplicate IDs! │
     * │ • Better to crash loudly than corrupt data silently │
     * └─────────────────────────────────────────────────────────────────────────┘
     * 
     * @return Allocated Node ID (0-1023)
     * @throws IllegalStateException if allocation fails (Redis unavailable, all
     *                               slots taken)
     */
    private int allocateNodeId() {

        // ═════════════════════════════════════════════════════════════════════
        // CHECK 1: Is Redis available?
        // ═════════════════════════════════════════════════════════════════════
        // redisTemplate is null if:
        // - Redis dependency not configured in pom.xml
        // - Redis host/port not set in application.yaml
        // - Redis server not running
        if (redisTemplate == null) {
            if (allowDevMode) {
                // ─────────────────────────────────────────────────────────────
                // DEV MODE: Allow running without Redis (NOT SAFE FOR PRODUCTION)
                // ─────────────────────────────────────────────────────────────
                // Use random Node ID between 0-1023
                // WARNING: If multiple instances run, they might pick same ID!
                logger.warn("⚠️  DEV MODE: Redis not available - using random Node ID. " +
                        "TSID uniqueness NOT guaranteed in multi-instance deployments!");
                int randomNodeId = (int) (Math.random() * MAX_NODE_ID);
                logger.info("Using random Node ID: {} (dev mode)", randomNodeId);
                return randomNodeId;
            } else {
                // ─────────────────────────────────────────────────────────────
                // PRODUCTION: FAIL-FAST - Redis is REQUIRED
                // ─────────────────────────────────────────────────────────────
                // Without Redis, we cannot guarantee unique Node IDs.
                // Multiple instances could use same ID → ID collisions!
                throw new IllegalStateException(
                        "Cannot allocate TSID nodeId – Redis unavailable. " +
                                "TSID requires Redis for distributed node allocation. " +
                                "Set tsid.allow-dev-mode=true for development (NOT recommended for production).");
            }
        }

        try {
            // ═════════════════════════════════════════════════════════════════
            // MAIN ALLOCATION LOOP: Try Node IDs 0 to 1023
            // ═════════════════════════════════════════════════════════════════
            for (int i = 0; i < MAX_NODE_ID; i++) {

                // Build Redis key: "sys:tsid:node:0", "sys:tsid:node:1", etc.
                String key = NODE_KEY_PREFIX + i;

                // ─────────────────────────────────────────────────────────────
                // ATOMIC LOCK ACQUISITION using Redis SETNX
                // ─────────────────────────────────────────────────────────────
                // setIfAbsent() = Redis SETNX command (Set if Not eXists)
                //
                // Parameters:
                // key = "sys:tsid:node:0"
                // value = "LOCKED" (could be any string, just marks as taken)
                // timeout = 24 hours (TTL - auto-expires if app crashes)
                //
                // Returns:
                // TRUE = Key didn't exist, we acquired it
                // FALSE = Key already exists (another instance has this ID)
                //
                // ATOMIC: Even if 2 instances call this at same time,
                // only ONE will get TRUE (Redis guarantees this)
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(key, "LOCKED", Duration.ofHours(24));

                if (Boolean.TRUE.equals(acquired)) {
                    // ─────────────────────────────────────────────────────────
                    // SUCCESS: We acquired this Node ID!
                    // ─────────────────────────────────────────────────────────

                    // Store key for scheduled refresh (keep-alive)
                    // The @Scheduled task will use this to extend TTL
                    allocatedNodeKey.set(key);

                    logger.info("Successfully allocated TSID Node ID: {} from Redis", i);
                    return i; // Return the acquired Node ID
                }

                // FALSE = This slot is taken, try the next one
                // Loop continues to try i+1
            }

            // ═════════════════════════════════════════════════════════════════
            // ALL 1024 SLOTS ARE TAKEN - FAIL-FAST
            // ═════════════════════════════════════════════════════════════════
            // This means 1024 instances are already running!
            // Extremely unlikely, but we handle it gracefully.
            throw new IllegalStateException(
                    String.format("Cannot allocate TSID nodeId – All Node IDs (0-%d) are in use. " +
                            "Maximum capacity reached. Please check for stale locks in Redis.",
                            MAX_NODE_ID - 1));

        } catch (RedisConnectionFailureException e) {
            // ═════════════════════════════════════════════════════════════════
            // REDIS CONNECTION FAILED during allocation
            // ═════════════════════════════════════════════════════════════════
            if (allowDevMode) {
                logger.warn("⚠️  DEV MODE: Redis connection failed - using random Node ID. " +
                        "TSID uniqueness NOT guaranteed!", e);
                int randomNodeId = (int) (Math.random() * MAX_NODE_ID);
                return randomNodeId;
            } else {
                throw new IllegalStateException(
                        "Cannot allocate TSID nodeId – Redis connection failed: " + e.getMessage() +
                                ". TSID requires Redis for distributed node allocation.",
                        e);
            }
        } catch (Exception e) {
            // ═════════════════════════════════════════════════════════════════
            // ANY OTHER REDIS ERROR
            // ═════════════════════════════════════════════════════════════════
            if (allowDevMode && !(e instanceof IllegalStateException)) {
                logger.warn("⚠️  DEV MODE: Redis error - using random Node ID. " +
                        "TSID uniqueness NOT guaranteed!", e);
                int randomNodeId = (int) (Math.random() * MAX_NODE_ID);
                return randomNodeId;
            }
            throw e; // Re-throw in production
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCHEDULED LOCK REFRESH (Keep-Alive)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Spring-managed scheduled task to refresh TSID node lock in Redis.
     * 
     * ┌─────────────────────────────────────────────────────────────────────────┐
     * │ WHY REFRESH? │
     * ├─────────────────────────────────────────────────────────────────────────┤
     * │ • Lock has 24-hour TTL (auto-expires if app crashes) │
     * │ • Refresh every 12 hours keeps lock alive while app runs │
     * │ • If app crashes, lock expires → slot becomes available │
     * │ • Safety margin: 12h between refreshes, 24h total TTL │
     * └─────────────────────────────────────────────────────────────────────────┘
     * 
     * ┌─────────────────────────────────────────────────────────────────────────┐
     * │ TIMELINE EXAMPLE │
     * ├─────────────────────────────────────────────────────────────────────────┤
     * │ 00:00 - App starts, acquires node:0, TTL = 24h (expires at 24:00) │
     * │ 12:00 - First refresh, TTL reset to 24h (now expires at 36:00) │
     * │ 24:00 - Second refresh, TTL reset to 24h (now expires at 48:00) │
     * │ ...continues every 12 hours while app runs... │
     * │ │
     * │ If app crashes at 15:00: │
     * │ - Last refresh was at 12:00, TTL was reset to 24h │
     * │ - Lock expires at 36:00 (12 + 24) │
     * │ - Another instance can claim this slot after 36:00 │
     * └─────────────────────────────────────────────────────────────────────────┘
     * 
     * ┌─────────────────────────────────────────────────────────────────────────┐
     * │ BENEFITS OVER MANUAL THREAD │
     * ├─────────────────────────────────────────────────────────────────────────┤
     * │ ✅ Spring-managed lifecycle (clean shutdown when app stops) │
     * │ ✅ Observable via Spring Actuator │
     * │ ✅ Better error handling and logging │
     * │ ✅ No daemon threads (uses Spring's task scheduler) │
     * └─────────────────────────────────────────────────────────────────────────┘
     */
    @Scheduled(fixedDelayString = "PT12H", // Run every 12 hours (ISO 8601 duration)
            initialDelayString = "PT12H" // First run after 12 hours (not immediately)
    )
    public void refreshTsidLock() {

        // Get the key we allocated during startup
        String key = allocatedNodeKey.get();

        // Skip if no key allocated (dev mode without Redis)
        if (key == null || redisTemplate == null) {
            return;
        }

        try {
            // ─────────────────────────────────────────────────────────────────
            // Extend TTL back to 24 hours
            // ─────────────────────────────────────────────────────────────────
            // Redis EXPIRE command: reset TTL without changing value
            redisTemplate.expire(key, Duration.ofHours(24));
            logger.debug("Refreshed TSID node lock: {}", key);

        } catch (Exception e) {
            // ─────────────────────────────────────────────────────────────────
            // Refresh failed - log error but don't crash
            // ─────────────────────────────────────────────────────────────────
            // Lock still has remaining TTL, but if this keeps failing,
            // the lock will eventually expire.
            logger.error("Failed to refresh TSID node lock: {}. " +
                    "Lock will expire in 24 hours. Application should restart to re-acquire lock.",
                    key, e);
        }
    }
}
