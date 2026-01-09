# TSID Implementation Guide - Complete Explanation (Enterprise-Grade)

## 📚 Table of Contents
1. [What is TSID?](#what-is-tsid)
2. [Architecture Overview](#architecture-overview)
3. [Component Breakdown](#component-breakdown)
4. [Enterprise-Grade Features](#enterprise-grade-features)
5. [Flow Diagram](#flow-diagram)
6. [Step-by-Step Execution](#step-by-step-execution)
7. [Configuration & Profiles](#configuration--profiles)

---

## What is TSID?

**TSID (Time-Sorted Unique Identifier)** is a 64-bit integer ID that combines:
- **Time (42 bits)**: Milliseconds since epoch - ensures IDs are sortable by time
- **Node ID (10 bits)**: Unique identifier for each backend instance (0-1023)
- **Sequence (12 bits)**: Counter for multiple IDs in the same millisecond (0-4095)

**Example TSID:** `1234567890123456789` (a Long number)

**Why TSID?**
- ✅ **Sortable**: IDs naturally sort by creation time
- ✅ **Unique**: No collisions across multiple servers
- ✅ **Fast**: BIGINT is faster than UUID strings for database indexes
- ✅ **Secure**: Hard to guess (unlike 1, 2, 3...)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Startup                       │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  TsidConfig.tsidFactory()                                    │
│  - Allocates Node ID from Redis (fail-fast if unavailable) │
│  - Creates TsidFactory with Node ID                         │
│  - Injects factory into TsidIdGenerator                     │
│  - Starts scheduled lock refresh task                         │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Entity with @TsidGenerator annotation                      │
│  - User, Post, Community, etc.                              │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Hibernate calls TsidIdGenerator.generate()                  │
│  - Validates factory is injected (fail-fast)                │
│  - Uses injected TsidFactory                                │
│  - Creates new TSID (Long)                                   │
│  - Returns to Hibernate                                      │
└─────────────────────────────────────────────────────────────┘
```

---

## Component Breakdown

### 1. `TsidGenerator.java` - The Annotation

```java
@IdGeneratorType(TsidIdGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({FIELD, METHOD})
public @interface TsidGenerator {
}
```

**What it does:**
- Custom annotation that tells Hibernate to use `TsidIdGenerator` for ID generation
- Replaces deprecated `@GenericGenerator` (Hibernate 7.2+ compatible)
- Can be placed on `@Id` fields or getter methods

**Usage in Entity:**
```java
@Entity
public class User {
    @Id
    @TsidGenerator  // ← This tells Hibernate to use our custom generator
    @Column(name = "internal_id")
    private Long internalId;
}
```

---

### 2. `TsidIdGenerator.java` - The Generator (Enterprise-Grade)

```java
public class TsidIdGenerator implements IdentifierGenerator {
    private static TsidFactory tsidFactory;
    
    public static void setTsidFactory(TsidFactory factory) {
        tsidFactory = factory;
        logger.info("TSID Factory injected into TsidIdGenerator");
    }
    
    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        if (tsidFactory == null) {
            // Enterprise-grade: Fail-fast instead of silent fallback
            throw new IllegalStateException(
                "TSID Factory not initialized. " +
                "TsidConfig.tsidFactory() must be called during Spring context initialization. " +
                "This indicates a configuration error - TSID cannot generate IDs without a configured factory."
            );
        }
        return tsidFactory.create().toLong();
    }
}
```

**What it does:**
- Implements Hibernate's `IdentifierGenerator` interface
- Called by Hibernate when a new entity needs an ID
- Uses the `TsidFactory` (injected from `TsidConfig`) to create TSIDs
- Returns a `Long` value (64-bit integer)

**Enterprise-Grade Features:**
- ✅ **Fail-fast**: Throws exception if factory not injected (prevents silent misconfiguration)
- ✅ **No fallback**: Won't create its own factory (ensures proper initialization)

**Execution Flow:**
1. Hibernate sees `@TsidGenerator` on an `@Id` field
2. Hibernate calls `generate(session, entityObject)`
3. Generator validates factory is injected (fail-fast if null)
4. Generator calls `tsidFactory.create().toLong()`
5. Returns the generated TSID as a Long
6. Hibernate sets this ID on the entity before saving

**Example:**
```java
User user = new User();
user.setEmail("test@example.com");
// At this point, user.getInternalId() is null

userRepository.save(user);
// Hibernate calls TsidIdGenerator.generate()
// Returns: 1234567890123456789L
// user.getInternalId() is now: 1234567890123456789L
```

---

### 3. `TsidConfig.java` - The Configuration (Enterprise-Grade)

This is the most complex component. Let's break it down:

#### 3.1. Class Structure

```java
@Configuration
@EnableScheduling  // ← Required for @Scheduled keep-alive
public class TsidConfig {
    @Autowired(required = false)  // ← Redis is optional (for dev mode check)
    private StringRedisTemplate redisTemplate;
    
    @Value("${tsid.allow-dev-mode:false}")  // ← Dev mode flag
    private boolean allowDevMode;
    
    private static final String NODE_KEY_PREFIX = "sys:tsid:node:";
    private static final int MAX_NODE_ID = 1024;  // 10 bits = 0-1023
    
    // Store allocated node key for scheduled refresh
    private final AtomicReference<String> allocatedNodeKey = new AtomicReference<>();
}
```

**Constants Explained:**
- `NODE_KEY_PREFIX`: Redis key pattern for node allocation
  - Example keys: `sys:tsid:node:0`, `sys:tsid:node:1`, ... `sys:tsid:node:1023`
- `MAX_NODE_ID`: Maximum number of backend instances (1024)
- `allowDevMode`: Allows fallback to random node ID in development (NOT for production)

#### 3.2. `tsidFactory()` Bean Method

```java
@Bean
public TsidFactory tsidFactory() {
    int nodeId = allocateNodeId();  // Step 1: Get a unique Node ID (fail-fast)
    
    // Step 2: Validate node ID range (0-1023)
    if (nodeId < 0 || nodeId >= MAX_NODE_ID) {
        throw new IllegalStateException(
            String.format("Invalid TSID Node ID: %d. Must be in range [0, %d)", 
                        nodeId, MAX_NODE_ID - 1)
        );
    }
    
    logger.info(">>> TSID Initialized with Node ID: {} (Range: 0-{})", nodeId, MAX_NODE_ID - 1);
    logger.info(">>> TSID Clock Synchronization: Ensure NTP is configured. " +
               "Clock drift > 1s may cause ID collisions.");
    
    // Step 3: Create factory with allocated node ID
    TsidFactory factory = TsidFactory.builder()
            .withNode(nodeId)
            .build();
    
    // Step 4: Inject factory into generator (static method)
    TsidIdGenerator.setTsidFactory(factory);
    
    return factory;  // Step 5: Return as Spring Bean
}
```

**What happens:**
1. **Allocates Node ID**: Calls `allocateNodeId()` to get a unique node ID (0-1023)
2. **Validates Range**: Ensures node ID is within valid range (fail-fast)
3. **Creates Factory**: Builds a `TsidFactory` with the allocated node ID
4. **Injects Factory**: Sets the factory in `TsidIdGenerator` (static method)
5. **Returns Bean**: Makes the factory available as a Spring bean

**When it runs:**
- During Spring Boot application startup
- Before any entities are created
- Only once per application instance

#### 3.3. `allocateNodeId()` - Node ID Allocation (Enterprise-Grade)

```java
private int allocateNodeId() {
    // Enterprise-grade: Fail-fast if Redis unavailable (unless dev mode)
    if (redisTemplate == null) {
        if (allowDevMode) {
            logger.warn("⚠️  DEV MODE: Redis not available - using random Node ID. " +
                       "TSID uniqueness NOT guaranteed in multi-instance deployments!");
            // Use random node ID in dev mode (better than fixed 0)
            int randomNodeId = (int) (Math.random() * MAX_NODE_ID);
            logger.info("Using random Node ID: {} (dev mode)", randomNodeId);
            return randomNodeId;
        } else {
            // Production: Fail-fast - Redis is required
            throw new IllegalStateException(
                "Cannot allocate TSID nodeId – Redis unavailable. " +
                "TSID requires Redis for distributed node allocation. " +
                "Set tsid.allow-dev-mode=true for development (NOT recommended for production)."
            );
        }
    }

    try {
        // Try to allocate node ID from Redis
        for (int i = 0; i < MAX_NODE_ID; i++) {
            String key = NODE_KEY_PREFIX + i;
            // Atomic operation: Only ONE instance can acquire this node ID
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, "LOCKED", Duration.ofHours(24));

            if (Boolean.TRUE.equals(acquired)) {
                allocatedNodeKey.set(key); // Store for scheduled refresh
                logger.info("Successfully allocated TSID Node ID: {} from Redis", i);
                return i;
            }
        }
        
        // All node IDs are taken - fail-fast
        throw new IllegalStateException(
            String.format("Cannot allocate TSID nodeId – All Node IDs (0-%d) are in use. " +
                        "Maximum capacity reached. Please check for stale locks in Redis.", 
                        MAX_NODE_ID - 1)
        );
    } catch (RedisConnectionFailureException e) {
        // Redis connection failed - fail-fast unless dev mode
        if (allowDevMode) {
            logger.warn("⚠️  DEV MODE: Redis connection failed - using random Node ID. " +
                       "TSID uniqueness NOT guaranteed!", e);
            int randomNodeId = (int) (Math.random() * MAX_NODE_ID);
            return randomNodeId;
        } else {
            throw new IllegalStateException(
                "Cannot allocate TSID nodeId – Redis connection failed: " + e.getMessage() +
                ". TSID requires Redis for distributed node allocation.",
                e
            );
        }
    }
}
```

**Enterprise-Grade Features:**
- ✅ **Fail-fast**: Throws exception if Redis unavailable (prevents silent collisions)
- ✅ **Dev mode**: Optional random node ID for local development (with warnings)
- ✅ **No silent fallback**: Won't use fixed node ID 0 (prevents multi-instance collisions)
- ✅ **Validation**: Ensures node ID is within valid range

**Detailed Explanation:**

**Step 1: Redis Check (Fail-Fast)**
```java
if (redisTemplate == null) {
    if (allowDevMode) {
        // Dev mode: Use random node ID (with warning)
        return randomNodeId;
    } else {
        // Production: Fail-fast - Redis is required
        throw new IllegalStateException(...);
    }
}
```
- **Production**: Throws exception if Redis unavailable (prevents collisions)
- **Dev Mode**: Uses random node ID (with warning) for local development
- ⚠️ **Warning**: Dev mode does NOT guarantee uniqueness in multi-instance deployments

**Step 2: Loop Through Node IDs**
```java
for (int i = 0; i < MAX_NODE_ID; i++) {
    String key = NODE_KEY_PREFIX + i;  // "sys:tsid:node:0"
```
- Tries node IDs from 0 to 1023
- Creates Redis key: `sys:tsid:node:0`, `sys:tsid:node:1`, etc.

**Step 3: Atomic Lock Acquisition**
```java
Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent(key, "LOCKED", Duration.ofHours(24));
```
- **`setIfAbsent`**: Redis command `SETNX` - only sets if key doesn't exist
- **Atomic Operation**: Guarantees only ONE instance can acquire a node ID
- **TTL (Time To Live)**: Key expires after 24 hours (auto-release if app crashes)
- **Returns**: 
  - `true` if key was set (node ID acquired)
  - `false` if key already exists (another instance has this node ID)

**Example Scenario:**
```
Instance 1 starts:
  - Tries "sys:tsid:node:0" → Key doesn't exist → Sets it → Returns true → Uses Node ID 0

Instance 2 starts (5 seconds later):
  - Tries "sys:tsid:node:0" → Key exists → Returns false → Tries next
  - Tries "sys:tsid:node:1" → Key doesn't exist → Sets it → Returns true → Uses Node ID 1

Instance 3 starts:
  - Tries "sys:tsid:node:0" → Exists → false
  - Tries "sys:tsid:node:1" → Exists → false
  - Tries "sys:tsid:node:2" → Doesn't exist → Sets it → Returns true → Uses Node ID 2
```

**Step 4: Store Key for Scheduled Refresh**
```java
if (Boolean.TRUE.equals(acquired)) {
    allocatedNodeKey.set(key); // Store for @Scheduled refresh
    return i;
}
```
- Stores the allocated key for scheduled lock refresh
- Returns the node ID to be used by `TsidFactory`

**Step 5: Error Handling (Fail-Fast)**
- If all node IDs are taken, throws exception (fail-fast)
- If Redis connection fails, throws exception (unless dev mode)
- No silent fallback to default node ID

#### 3.4. `refreshTsidLock()` - Scheduled Lock Refresh (Enterprise-Grade)

```java
/**
 * Spring-managed scheduled task to refresh TSID node lock in Redis.
 * Runs every 12 hours to keep the lock alive (TTL: 24 hours).
 * 
 * Benefits over manual thread:
 * - Spring-managed lifecycle (clean shutdown)
 * - Observable via Spring Actuator
 * - Better error handling and logging
 */
@Scheduled(fixedDelayString = "PT12H", initialDelayString = "PT12H")
public void refreshTsidLock() {
    String key = allocatedNodeKey.get();
    if (key == null || redisTemplate == null) {
        return; // No node allocated or Redis unavailable
    }
    
    try {
        redisTemplate.expire(key, Duration.ofHours(24));
        logger.debug("Refreshed TSID node lock: {}", key);
    } catch (Exception e) {
        logger.error("Failed to refresh TSID node lock: {}. " +
                    "Lock will expire in 24 hours. Application should restart to re-acquire lock.", 
                    key, e);
    }
}
```

**What it does:**
- **Spring-Managed**: Uses `@Scheduled` annotation (not manual thread)
- **Refreshes Lock**: Every 12 hours, extends the Redis key TTL to 24 hours
- **Prevents Expiration**: Ensures the node ID lock doesn't expire while app is running
- **Clean Shutdown**: Spring manages lifecycle (proper shutdown on app stop)

**Why 12 hours refresh with 24 hour TTL?**
- If app crashes, lock expires after 24 hours (auto-release)
- If app is running, refresh happens every 12 hours (before expiration)
- Provides safety margin: even if refresh fails, there's still 12 hours before expiration

**Benefits over Manual Thread:**
- ✅ **Spring-managed lifecycle**: Clean shutdown when app stops
- ✅ **Observable**: Can be monitored via Spring Actuator
- ✅ **Better error handling**: Spring handles exceptions gracefully
- ✅ **No daemon threads**: Uses Spring's task scheduler

**Example Timeline:**
```
00:00 - App starts, acquires node ID 0, sets TTL to 24h
12:00 - @Scheduled task refreshes TTL to 24h (now expires at 36:00)
24:00 - @Scheduled task refreshes TTL to 24h (now expires at 48:00)
... continues every 12 hours while app runs
```

---

### 4. `TsidHealthIndicator.java` - Health Monitoring

```java
@Component
public class TsidHealthIndicator implements HealthIndicator {
    private final TsidFactory tsidFactory;
    private final AtomicInteger nodeId = new AtomicInteger(-1);
    
    @Override
    public Health health() {
        // Test TSID generation
        // Check clock synchronization
        // Return health status with details
    }
}
```

**What it does:**
- **Health Monitoring**: Exposes TSID system status via Spring Boot Actuator
- **Clock Sync Check**: Warns if system clock is out of sync (> 1 second drift)
- **Test Generation**: Verifies TSID factory can generate IDs
- **Accessible**: `GET /actuator/health` endpoint

**Spring Boot 4.0 Changes:**
- Uses `org.springframework.boot.health.contributor.HealthIndicator` (new package)
- Uses `Health.down().withDetails(Map)` API (changed from `withDetail()`)

---

## Enterprise-Grade Features

### 1. Fail-Fast Principle

**❌ Old Approach (Silent Fallback):**
```java
if (redisTemplate == null) {
    return DEFAULT_NODE_ID;  // Silent fallback - dangerous!
}
```

**✅ New Approach (Fail-Fast):**
```java
if (redisTemplate == null) {
    if (allowDevMode) {
        // Dev mode: Random node ID with warning
        logger.warn("⚠️  DEV MODE: Redis not available...");
        return randomNodeId;
    } else {
        // Production: Fail-fast
        throw new IllegalStateException("Redis unavailable - TSID requires Redis");
    }
}
```

**Benefits:**
- Prevents silent collisions in production
- Forces proper configuration
- Clear error messages

### 2. Spring-Managed Scheduling

**❌ Old Approach (Manual Thread):**
```java
Thread keepAliveThread = new Thread(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        // Manual thread management
    }
});
keepAliveThread.setDaemon(true);
keepAliveThread.start();
```

**✅ New Approach (@Scheduled):**
```java
@Scheduled(fixedDelayString = "PT12H", initialDelayString = "PT12H")
public void refreshTsidLock() {
    // Spring-managed lifecycle
}
```

**Benefits:**
- Clean shutdown
- Observable via Actuator
- Better error handling

### 3. Strict Factory Injection

**❌ Old Approach (Silent Fallback):**
```java
if (tsidFactory == null) {
    tsidFactory = TsidFactory.builder().build();  // Creates own factory
}
```

**✅ New Approach (Fail-Fast):**
```java
if (tsidFactory == null) {
    throw new IllegalStateException("TSID Factory not initialized");
}
```

**Benefits:**
- Prevents silent misconfiguration
- Ensures proper initialization
- Clear error messages

### 4. Development Mode

**Configuration:**
```yaml
# application-dev.yaml
tsid:
  allow-dev-mode: true  # ⚠️ CHỈ dùng cho local dev
```

**Behavior:**
- Allows random node ID if Redis unavailable
- Logs warnings about uniqueness
- NOT recommended for production

---

## Flow Diagram

### Complete Flow: From Entity Save to TSID Generation

```
┌─────────────────────────────────────────────────────────────┐
│  Developer Code                                             │
│  User user = new User();                                    │
│  user.setEmail("test@example.com");                         │
│  userRepository.save(user);  ← Save entity                 │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Hibernate Interceptor                                      │
│  - Detects @Id field with @TsidGenerator                   │
│  - Sees internalId is null                                  │
│  - Needs to generate ID                                     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  TsidIdGenerator.generate(session, user)                   │
│  - Validates factory is injected (fail-fast)              │
│  - Gets static tsidFactory (injected at startup)            │
│  - Calls: tsidFactory.create()                              │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  TsidFactory.create()                                       │
│  - Gets current timestamp (42 bits)                         │
│  - Gets Node ID (10 bits) - from TsidConfig                │
│  - Gets Sequence (12 bits) - auto-increment                  │
│  - Combines: Time | Node | Sequence                         │
│  - Returns: TSID object                                     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  .toLong()                                                  │
│  - Converts TSID to Long (64-bit integer)                   │
│  - Example: 1234567890123456789L                            │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Hibernate sets ID on entity                                │
│  user.setInternalId(1234567890123456789L)                   │
│  - Entity now has ID                                        │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Hibernate saves to database                                 │
│  INSERT INTO users (internal_id, email, ...)                │
│  VALUES (1234567890123456789, 'test@example.com', ...)     │
└─────────────────────────────────────────────────────────────┘
```

---

## Step-by-Step Execution

### Scenario: Starting 3 Backend Instances

#### Instance 1 Startup:

```
1. Spring Boot starts
2. TsidConfig.tsidFactory() is called
3. allocateNodeId() executes:
   - Redis available? Yes
   - Loop: i = 0
     - Key: "sys:tsid:node:0"
     - setIfAbsent("sys:tsid:node:0", "LOCKED", 24h) → true (key didn't exist)
     - allocatedNodeKey.set("sys:tsid:node:0")
     - Returns: 0
4. TsidFactory created with Node ID = 0
5. Factory injected into TsidIdGenerator
6. @Scheduled task registered (runs every 12 hours)
7. App ready, logs: "TSID Initialized with Node ID: 0"
```

#### Instance 2 Startup (5 seconds later):

```
1. Spring Boot starts
2. TsidConfig.tsidFactory() is called
3. allocateNodeId() executes:
   - Redis available? Yes
   - Loop: i = 0
     - Key: "sys:tsid:node:0"
     - setIfAbsent("sys:tsid:node:0", "LOCKED", 24h) → false (key exists!)
     - Continue loop
   - Loop: i = 1
     - Key: "sys:tsid:node:1"
     - setIfAbsent("sys:tsid:node:1", "LOCKED", 24h) → true (key didn't exist)
     - allocatedNodeKey.set("sys:tsid:node:1")
     - Returns: 1
4. TsidFactory created with Node ID = 1
5. Factory injected into TsidIdGenerator
6. @Scheduled task registered
7. App ready, logs: "TSID Initialized with Node ID: 1"
```

#### Instance 3 Startup:

```
1. Spring Boot starts
2. TsidConfig.tsidFactory() is called
3. allocateNodeId() executes:
   - Redis available? Yes
   - Loop: i = 0 → false (taken)
   - Loop: i = 1 → false (taken)
   - Loop: i = 2
     - Key: "sys:tsid:node:2"
     - setIfAbsent("sys:tsid:node:2", "LOCKED", 24h) → true
     - allocatedNodeKey.set("sys:tsid:node:2")
     - Returns: 2
4. TsidFactory created with Node ID = 2
5. Factory injected into TsidIdGenerator
6. @Scheduled task registered
7. App ready, logs: "TSID Initialized with Node ID: 2"
```

### Scenario: Creating a User

```
1. Code: userRepository.save(newUser)
2. Hibernate: "I need an ID for this User entity"
3. Hibernate: "I see @TsidGenerator on internalId field"
4. Hibernate: "Call TsidIdGenerator.generate()"
5. TsidIdGenerator: "Validate factory is injected (fail-fast if null)"
6. TsidIdGenerator: "Get static tsidFactory"
7. TsidFactory: "Create TSID with Node ID = 0 (from Instance 1)"
8. TSID created: Time(42 bits) | Node(10 bits) | Sequence(12 bits)
   Example: 1234567890123456789L
9. Returns: 1234567890123456789L
10. Hibernate: "Set user.internalId = 1234567890123456789L"
11. Hibernate: "INSERT INTO users (internal_id, ...) VALUES (1234567890123456789, ...)"
12. Database: "Saved with TSID: 1234567890123456789"
```

---

## Configuration & Profiles

### Development Profile (`application-dev.yaml`)

```yaml
tsid:
  allow-dev-mode: true  # ⚠️ CHỈ dùng cho local dev

spring:
  data:
    redis:
      host: localhost
      port: 6379
      # No password for local dev
```

**Usage:**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Production Profile (`application-prod.yaml`)

```yaml
tsid:
  allow-dev-mode: false  # ⚠️ MUST be false in production

spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
      # Use Redis Cluster/Sentinel for HA
```

**Usage:**
```bash
export SPRING_PROFILES_ACTIVE=prod
export REDIS_HOST=redis-cluster.production.internal
export REDIS_PASSWORD=secure_password
java -jar app.jar
```

---

## Key Concepts Summary

### 1. **Node ID Allocation (Redis)**
- **Purpose**: Ensure each backend instance has a unique node ID
- **Method**: Atomic Redis `SETNX` operation
- **Result**: No two instances share the same node ID
- **Enterprise**: Fail-fast if Redis unavailable (unless dev mode)

### 2. **TSID Generation (TsidFactory)**
- **Purpose**: Create unique, sortable 64-bit IDs
- **Components**: Time (42 bits) + Node (10 bits) + Sequence (12 bits)
- **Result**: Long integer that's globally unique and time-sortable

### 3. **Hibernate Integration (TsidIdGenerator)**
- **Purpose**: Bridge between Hibernate and TSID generation
- **Method**: Implements `IdentifierGenerator` interface
- **Result**: Hibernate automatically generates TSIDs for entities
- **Enterprise**: Fail-fast if factory not injected

### 4. **Lock Management (@Scheduled)**
- **Purpose**: Keep node ID lock alive while app runs
- **Method**: Spring `@Scheduled` task refreshes Redis TTL every 12 hours
- **Result**: Lock doesn't expire while app is running
- **Enterprise**: Spring-managed lifecycle (clean shutdown)

---

## Common Questions

### Q: What happens if Redis crashes?
**A:** The app continues running with its current node ID. The scheduled refresh task will fail, but the lock has 24 hours TTL. If Redis is down for >24 hours, the lock expires and another instance could take that node ID (but your app would likely be down too).

### Q: What happens if two instances try to get the same node ID?
**A:** Redis `SETNX` is atomic - only ONE will succeed. The other will try the next available node ID.

### Q: Can I manually set a node ID?
**A:** Not with the current implementation. The system auto-discovers node IDs from Redis. You could modify `TsidConfig` to check an environment variable first, then fall back to Redis.

### Q: What's the maximum number of instances?
**A:** 1024 instances (node IDs 0-1023). Each can generate 4096 IDs per millisecond, so total capacity is ~4 million IDs per second across all instances.

### Q: Why use Redis instead of configuration?
**A:** 
- **Auto-scaling**: No need to configure node IDs manually
- **Dynamic**: Instances can start/stop without manual intervention
- **Safe**: Atomic operations prevent collisions
- **Self-healing**: Locks expire if instance crashes

### Q: What's the difference between dev mode and production?
**A:**
- **Dev Mode**: Allows random node ID if Redis unavailable (with warnings)
- **Production**: Fail-fast if Redis unavailable (prevents collisions)

### Q: Why fail-fast instead of fallback?
**A:** 
- **Prevents silent collisions**: Multiple instances won't accidentally share node ID 0
- **Forces proper configuration**: Ensures Redis is configured correctly
- **Clear error messages**: Makes debugging easier

---

## Testing TSID

### Check Node ID Allocation:
```bash
# Connect to Redis
redis-cli

# Check allocated node IDs
KEYS sys:tsid:node:*

# Check specific node
GET sys:tsid:node:0
TTL sys:tsid:node:0  # Time remaining until expiration
```

### Verify TSID Generation:
```java
// In your service/controller
User user = new User();
user.setEmail("test@example.com");
User saved = userRepository.save(user);

System.out.println("Generated TSID: " + saved.getInternalId());
// Output: Generated TSID: 1234567890123456789
```

### Check TSID Structure:
```java
// TSID can be decoded to see its components
TSID tsid = TSID.from(saved.getInternalId());
System.out.println("Time: " + tsid.getTime());      // Milliseconds since epoch
System.out.println("Node: " + tsid.getNode());      // Node ID (0-1023)
System.out.println("Sequence: " + tsid.getSequence()); // Sequence (0-4095)
```

### Check Health Status:
```bash
# Via Actuator endpoint
curl http://localhost:8080/actuator/health

# Response includes TSID health:
{
  "status": "UP",
  "components": {
    "tsid": {
      "status": "UP",
      "details": {
        "status": "TSID system operational",
        "nodeId": 0,
        "testTsid": 1234567890123456789,
        "maxNodeId": 1023,
        "nodeCapacity": "1024 instances"
      }
    }
  }
}
```

---

## Summary

The TSID system in your backend (Enterprise-Grade):

1. **Starts up**: Allocates unique node ID from Redis (fail-fast if unavailable)
2. **Configures**: Creates `TsidFactory` with node ID
3. **Integrates**: Injects factory into `TsidIdGenerator` for Hibernate
4. **Schedules**: Registers `@Scheduled` task for lock refresh
5. **Generates**: When entities are saved, Hibernate calls generator
6. **Validates**: Generator validates factory is injected (fail-fast)
7. **Creates**: Generator uses factory to create unique TSID
8. **Saves**: Entity gets TSID as its primary key
9. **Refreshes**: Scheduled task keeps lock alive every 12 hours

**Result**: Every entity gets a unique, sortable 64-bit ID that works across multiple backend instances with enterprise-grade reliability! 🎉

---

## Enterprise-Grade Checklist

- ✅ Fail-fast node allocation (no silent fallback)
- ✅ Spring-managed scheduling (clean shutdown)
- ✅ Strict factory injection (no silent misconfiguration)
- ✅ Development mode support (with warnings)
- ✅ Health monitoring (Actuator integration)
- ✅ Clock synchronization warnings
- ✅ Proper error handling and logging
- ✅ Configuration profiles (dev/staging/prod)
