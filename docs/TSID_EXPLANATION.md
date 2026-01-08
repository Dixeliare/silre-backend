# TSID Implementation Guide - Complete Explanation

## 📚 Table of Contents
1. [What is TSID?](#what-is-tsid)
2. [Architecture Overview](#architecture-overview)
3. [Component Breakdown](#component-breakdown)
4. [Flow Diagram](#flow-diagram)
5. [Step-by-Step Execution](#step-by-step-execution)

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
│  - Allocates Node ID from Redis (or uses default)          │
│  - Creates TsidFactory with Node ID                         │
│  - Injects factory into TsidIdGenerator                     │
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

### 2. `TsidIdGenerator.java` - The Generator

```java
public class TsidIdGenerator implements IdentifierGenerator {
    private static TsidFactory tsidFactory;
    
    public static void setTsidFactory(TsidFactory factory) {
        tsidFactory = factory;
    }
    
    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        if (tsidFactory == null) {
            tsidFactory = TsidFactory.builder().build();
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

**Execution Flow:**
1. Hibernate sees `@TsidGenerator` on an `@Id` field
2. Hibernate calls `generate(session, entityObject)`
3. Generator calls `tsidFactory.create().toLong()`
4. Returns the generated TSID as a Long
5. Hibernate sets this ID on the entity before saving

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

### 3. `TsidConfig.java` - The Configuration

This is the most complex component. Let's break it down:

#### 3.1. Class Structure

```java
@Configuration
public class TsidConfig {
    @Autowired(required = false)  // ← Redis is optional
    private StringRedisTemplate redisTemplate;
    
    private static final String NODE_KEY_PREFIX = "sys:tsid:node:";
    private static final int MAX_NODE_ID = 1024;  // 10 bits = 0-1023
    private static final int DEFAULT_NODE_ID = 0; // Fallback
}
```

**Constants Explained:**
- `NODE_KEY_PREFIX`: Redis key pattern for node allocation
  - Example keys: `sys:tsid:node:0`, `sys:tsid:node:1`, ... `sys:tsid:node:1023`
- `MAX_NODE_ID`: Maximum number of backend instances (1024)
- `DEFAULT_NODE_ID`: Used when Redis is unavailable

#### 3.2. `tsidFactory()` Bean Method

```java
@Bean
public TsidFactory tsidFactory() {
    int nodeId = allocateNodeId();  // Step 1: Get a unique Node ID
    logger.info(">>> TSID Initialized with Node ID: {}", nodeId);
    
    TsidFactory factory = TsidFactory.builder()
            .withNode(nodeId)  // Step 2: Configure factory with Node ID
            .build();
    
    TsidIdGenerator.setTsidFactory(factory);  // Step 3: Inject into generator
    
    return factory;  // Step 4: Return as Spring Bean
}
```

**What happens:**
1. **Allocates Node ID**: Calls `allocateNodeId()` to get a unique node ID (0-1023)
2. **Creates Factory**: Builds a `TsidFactory` with the allocated node ID
3. **Injects Factory**: Sets the factory in `TsidIdGenerator` (static method)
4. **Returns Bean**: Makes the factory available as a Spring bean

**When it runs:**
- During Spring Boot application startup
- Before any entities are created
- Only once per application instance

#### 3.3. `allocateNodeId()` - Node ID Allocation

```java
private int allocateNodeId() {
    // Step 1: Check if Redis is available
    if (redisTemplate == null) {
        logger.warn("Redis not available - using default Node ID: 0");
        return DEFAULT_NODE_ID;  // Return 0 if no Redis
    }

    try {
        // Step 2: Loop through possible node IDs (0 to 1023)
        for (int i = 0; i < MAX_NODE_ID; i++) {
            String key = NODE_KEY_PREFIX + i;  // "sys:tsid:node:0", "sys:tsid:node:1", etc.
            
            // Step 3: Try to acquire this node ID
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, "LOCKED", Duration.ofHours(24));
            
            // Step 4: If successfully acquired, use this node ID
            if (Boolean.TRUE.equals(acquired)) {
                keepAlive(key);  // Start background thread to refresh lock
                logger.info("Successfully allocated TSID Node ID: {} from Redis", i);
                return i;  // Return the node ID
            }
            // If key exists, try next node ID
        }
        
        // Step 5: All node IDs are taken (shouldn't happen in practice)
        logger.warn("All TSID Node IDs are in use. Using default: 0");
        return DEFAULT_NODE_ID;
        
    } catch (RedisConnectionFailureException e) {
        // Step 6: Redis connection failed - use fallback
        logger.warn("Unable to connect to Redis. Using default Node ID: 0");
        return DEFAULT_NODE_ID;
    }
}
```

**Detailed Explanation:**

**Step 1: Redis Check**
```java
if (redisTemplate == null) {
    return DEFAULT_NODE_ID;  // Use node ID 0 if Redis unavailable
}
```
- If Redis isn't configured or unavailable, use default node ID 0
- This allows the app to start without Redis (development mode)
- ⚠️ **Warning**: Multiple instances without Redis will all use node ID 0 (collision risk!)

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

**Step 4: Start Keep-Alive Thread**
```java
if (Boolean.TRUE.equals(acquired)) {
    keepAlive(key);  // Start background thread
    return i;
}
```
- If node ID acquired, start a background thread to keep the lock alive
- Returns the node ID to be used by `TsidFactory`

**Step 5: Error Handling**
- If Redis connection fails, catches exception and uses default node ID 0
- Logs warning so you know what happened

#### 3.4. `keepAlive()` - Lock Refresh Thread

```java
private void keepAlive(String key) {
    if (redisTemplate == null) {
        return;  // Skip if Redis unavailable
    }
    
    Thread keepAliveThread = new Thread(() -> {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(Duration.ofHours(12).toMillis());  // Wait 12 hours
                if (redisTemplate != null) {
                    redisTemplate.expire(key, Duration.ofHours(24));  // Refresh TTL to 24h
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;  // Stop thread
            } catch (Exception e) {
                logger.warn("Error refreshing TSID node lock: {}", e.getMessage());
                break;  // Stop on error
            }
        }
    });
    keepAliveThread.setDaemon(true);  // Don't prevent JVM shutdown
    keepAliveThread.setName("TSID-KeepAlive-" + key);
    keepAliveThread.start();
}
```

**What it does:**
- **Background Thread**: Runs continuously while app is running
- **Refreshes Lock**: Every 12 hours, extends the Redis key TTL to 24 hours
- **Prevents Expiration**: Ensures the node ID lock doesn't expire while app is running
- **Daemon Thread**: Doesn't prevent JVM shutdown (dies when app stops)

**Why 12 hours refresh with 24 hour TTL?**
- If app crashes, lock expires after 24 hours (auto-release)
- If app is running, refresh happens every 12 hours (before expiration)
- Provides safety margin: even if refresh fails, there's still 12 hours before expiration

**Example Timeline:**
```
00:00 - App starts, acquires node ID 0, sets TTL to 24h
12:00 - Keep-alive thread refreshes TTL to 24h (now expires at 36:00)
24:00 - Keep-alive thread refreshes TTL to 24h (now expires at 48:00)
... continues every 12 hours while app runs
```

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
     - keepAlive("sys:tsid:node:0") → starts background thread
     - Returns: 0
4. TsidFactory created with Node ID = 0
5. Factory injected into TsidIdGenerator
6. App ready, logs: "TSID Initialized with Node ID: 0"
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
     - keepAlive("sys:tsid:node:1") → starts background thread
     - Returns: 1
4. TsidFactory created with Node ID = 1
5. Factory injected into TsidIdGenerator
6. App ready, logs: "TSID Initialized with Node ID: 1"
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
     - keepAlive("sys:tsid:node:2") → starts background thread
     - Returns: 2
4. TsidFactory created with Node ID = 2
5. Factory injected into TsidIdGenerator
6. App ready, logs: "TSID Initialized with Node ID: 2"
```

### Scenario: Creating a User

```
1. Code: userRepository.save(newUser)
2. Hibernate: "I need an ID for this User entity"
3. Hibernate: "I see @TsidGenerator on internalId field"
4. Hibernate: "Call TsidIdGenerator.generate()"
5. TsidIdGenerator: "Get static tsidFactory"
6. TsidFactory: "Create TSID with Node ID = 0 (from Instance 1)"
7. TSID created: Time(42 bits) | Node(10 bits) | Sequence(12 bits)
   Example: 1234567890123456789L
8. Returns: 1234567890123456789L
9. Hibernate: "Set user.internalId = 1234567890123456789L"
10. Hibernate: "INSERT INTO users (internal_id, ...) VALUES (1234567890123456789, ...)"
11. Database: "Saved with TSID: 1234567890123456789"
```

---

## Key Concepts Summary

### 1. **Node ID Allocation (Redis)**
- **Purpose**: Ensure each backend instance has a unique node ID
- **Method**: Atomic Redis `SETNX` operation
- **Result**: No two instances share the same node ID
- **Fallback**: Uses node ID 0 if Redis unavailable

### 2. **TSID Generation (TsidFactory)**
- **Purpose**: Create unique, sortable 64-bit IDs
- **Components**: Time (42 bits) + Node (10 bits) + Sequence (12 bits)
- **Result**: Long integer that's globally unique and time-sortable

### 3. **Hibernate Integration (TsidIdGenerator)**
- **Purpose**: Bridge between Hibernate and TSID generation
- **Method**: Implements `IdentifierGenerator` interface
- **Result**: Hibernate automatically generates TSIDs for entities

### 4. **Lock Management (keepAlive)**
- **Purpose**: Keep node ID lock alive while app runs
- **Method**: Background thread refreshes Redis TTL every 12 hours
- **Result**: Lock doesn't expire while app is running

---

## Common Questions

### Q: What happens if Redis crashes?
**A:** The app continues running with its current node ID. The keep-alive thread will fail to refresh, but the lock has 24 hours TTL. If Redis is down for >24 hours, the lock expires and another instance could take that node ID (but your app would likely be down too).

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

---

## Summary

The TSID system in your backend:

1. **Starts up**: Allocates unique node ID from Redis (or uses 0)
2. **Configures**: Creates `TsidFactory` with node ID
3. **Integrates**: Injects factory into `TsidIdGenerator` for Hibernate
4. **Generates**: When entities are saved, Hibernate calls generator
5. **Creates**: Generator uses factory to create unique TSID
6. **Saves**: Entity gets TSID as its primary key

**Result**: Every entity gets a unique, sortable 64-bit ID that works across multiple backend instances! 🎉
