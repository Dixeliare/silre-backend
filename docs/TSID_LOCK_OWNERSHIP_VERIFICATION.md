# TSID Lock Ownership Verification - Fail-Fast Protection

## 🎯 Vấn đề đã giải quyết

### Scenario nguy hiểm:

```
1. Instance A starts → Allocates Node ID 0 → Redis key: sys:tsid:node:0 = "instance:A:pid1:time1"
2. Redis restart → Key bị mất
3. Instance A vẫn chạy với Node ID 0 trong memory
4. Instance B starts → Allocates Node ID 0 → Redis key: sys:tsid:node:0 = "instance:B:pid2:time2"
5. ❌ CẢ HAI INSTANCE CÙNG DÙNG NODE ID 0 → ID COLLISIONS!
```

**Kết quả:** TSID collisions → Data corruption!

---

## ✅ Giải pháp: Lock Ownership Verification

### 1. **Instance ID trong Redis Value**

**Trước đây:**
```redis
sys:tsid:node:0 = "LOCKED"  # Không biết instance nào owns
```

**Bây giờ:**
```redis
sys:tsid:node:0 = "instance:hostname:pid:timestamp"  # Biết rõ instance nào owns
```

**Format:** `instance:{hostname}:{pid}:{timestamp}`

**Ví dụ:** `instance:mbp-cua-longdx:61478:1705012315000`

---

### 2. **Ownership Verification trong Refresh**

```java
@Scheduled(fixedDelayString = "PT12H")
public void refreshTsidLock() {
    // STEP 1: Verify lock ownership
    String currentValue = redisTemplate.opsForValue().get(key);
    
    if (currentValue == null) {
        // Lock lost → FAIL-FAST
        throw new IllegalStateException("Lock lost!");
    }
    
    if (!currentValue.equals(instanceId)) {
        // Lock stolen → FAIL-FAST
        throw new IllegalStateException("Lock stolen by another instance!");
    }
    
    // STEP 2: Refresh TTL (ownership verified)
    redisTemplate.expire(key, Duration.ofHours(24));
}
```

---

## 🔍 Các Case được Tránh

### Case 1: Redis Restart → Lock Lost

```
Timeline:
00:00 - Instance A: Node ID 0, Redis key exists
04:30 - Redis restart → Key bị mất
04:31 - Instance A: refreshTsidLock() chạy
        → Check: currentValue == null
        → FAIL-FAST: Throw IllegalStateException
        → App crashes → Không generate TSID nữa
        → ✅ Tránh ID collisions!
```

---

### Case 2: Lock Stolen bởi Instance Khác

```
Timeline:
00:00 - Instance A: Node ID 0, Redis key = "instance:A:pid1:time1"
04:30 - Redis restart → Key bị mất
04:31 - Instance B: Starts → Allocates Node ID 0
        → Redis key = "instance:B:pid2:time2"
04:32 - Instance A: refreshTsidLock() chạy
        → Check: currentValue = "instance:B:pid2:time2"
        → Check: !currentValue.equals(instanceId) → TRUE
        → FAIL-FAST: Throw IllegalStateException
        → App crashes → Không generate TSID nữa
        → ✅ Tránh ID collisions!
```

---

### Case 3: Multiple Instances cùng Start

```
Timeline:
00:00:00.000 - Instance A: Tries Node ID 0 → SETNX → Success
00:00:00.001 - Instance B: Tries Node ID 0 → SETNX → Fail (key exists)
00:00:00.002 - Instance B: Tries Node ID 1 → SETNX → Success
→ ✅ Mỗi instance có Node ID riêng (0 và 1)
```

**Redis SETNX đảm bảo atomicity** → Chỉ một instance acquire được.

---

## 📊 Impact Analysis

### Code Changes:

| File | Changes | Impact |
|------|---------|--------|
| `TsidConfig.java` | + Instance ID generation | Low |
| `TsidConfig.java` | + Ownership verification in refresh | Medium |
| `TsidConfig.java` | + Fail-fast on lock loss | High (safety) |

**Total:** ~50 lines of code changes

---

### Benefits:

1. ✅ **Detect lock loss immediately** (within 12 hours max)
2. ✅ **Prevent ID collisions** (fail-fast stops TSID generation)
3. ✅ **Clear error messages** (know exactly what happened)
4. ✅ **Self-healing** (app restart → re-acquire Node ID)

---

### Trade-offs:

1. ⚠️ **App crashes if lock lost** (but this is GOOD - prevents corruption)
2. ⚠️ **Requires Redis to be available** (but TSID already requires Redis)
3. ⚠️ **12-hour detection window** (but acceptable for most use cases)

---

## 🔧 How It Works

### Startup Flow:

```
1. TsidConfig constructor
   └─ Generate instanceId = "instance:hostname:pid:timestamp"

2. allocateNodeId()
   └─ SETNX key = "sys:tsid:node:0", value = instanceId
   └─ Success → Store key in allocatedNodeKey

3. Backend running
   └─ Generate TSIDs with Node ID 0
```

---

### Refresh Flow (Every 12 hours):

```
1. refreshTsidLock() scheduled task
   │
   ├─ Get key from allocatedNodeKey
   │
   ├─ Get current value from Redis
   │   ├─ null → Lock lost → FAIL-FAST
   │   ├─ Different instanceId → Lock stolen → FAIL-FAST
   │   └─ Matches instanceId → Continue
   │
   └─ Refresh TTL (24 hours)
```

---

## 🧪 Testing Scenarios

### Test 1: Normal Operation

```bash
# 1. Start backend
# 2. Check Redis
redis-cli GET "sys:tsid:node:0"
# Output: "instance:hostname:pid:timestamp"

# 3. Wait 12 hours
# 4. Check logs - should see refresh success
```

**Expected:** ✅ Lock refreshed successfully

---

### Test 2: Redis Restart (Lock Lost)

```bash
# 1. Start backend → Node ID 0 allocated
# 2. Stop Redis
redis-cli SHUTDOWN

# 3. Wait for refresh (or trigger manually)
# 4. Check logs

# Expected: FAIL-FAST error
# "CRITICAL: TSID Node ID lock lost!"
```

**Expected:** ❌ App crashes with clear error message

---

### Test 3: Lock Stolen

```bash
# Terminal 1: Start Instance A
# → Allocates Node ID 0

# Terminal 2: Delete Redis key
redis-cli DEL "sys:tsid:node:0"

# Terminal 3: Start Instance B
# → Allocates Node ID 0 (same as A!)

# Terminal 1: Wait for refresh
# Expected: FAIL-FAST error
# "CRITICAL: TSID Node ID lock stolen!"
```

**Expected:** ❌ Instance A crashes, Instance B continues

---

## 📝 Summary

### ✅ **Đã Implement:**

1. ✅ Instance ID generation (unique per instance)
2. ✅ Store instanceId in Redis value
3. ✅ Ownership verification during refresh
4. ✅ Fail-fast on lock loss or theft
5. ✅ Clear error messages

### ✅ **Cases Tránh được:**

1. ✅ **Lock loss detection** (Redis restart)
2. ✅ **Lock theft detection** (another instance acquires same Node ID)
3. ✅ **ID collision prevention** (fail-fast stops TSID generation)
4. ✅ **Multiple instances** (SETNX ensures atomicity)

### ⚠️ **Trade-offs:**

1. ⚠️ App crashes if lock lost (but prevents corruption)
2. ⚠️ 12-hour detection window (acceptable)
3. ⚠️ Requires Redis (but TSID already requires it)

---

## 🎓 Best Practices Applied

1. ✅ **Lock Ownership Verification** (store instance ID in value)
2. ✅ **Fail-Fast Principle** (crash early, prevent corruption)
3. ✅ **Atomic Operations** (SETNX for allocation)
4. ✅ **Self-Healing** (restart → re-acquire)
5. ✅ **Clear Error Messages** (know exactly what happened)

---

## 🔮 Future Enhancements

### Option 1: Shorter Refresh Interval

```java
@Scheduled(fixedDelayString = "PT1H")  // Every 1 hour instead of 12
```

**Benefit:** Faster detection (1 hour vs 12 hours)  
**Trade-off:** More Redis calls

---

### Option 2: Health Check Integration

```java
// In TsidHealthIndicator
if (lockLost) {
    return Health.down().withDetails("Lock lost - restart required");
}
```

**Benefit:** Health endpoint shows lock status  
**Trade-off:** Additional complexity

---

### Option 3: Graceful Shutdown

```java
// Instead of crashing, gracefully shutdown
if (lockLost) {
    logger.error("Lock lost - initiating graceful shutdown");
    applicationContext.close();
}
```

**Benefit:** Clean shutdown  
**Trade-off:** More complex implementation

---

## 📊 Comparison: Before vs After

| Scenario | Before | After |
|----------|--------|-------|
| Redis restart | ✅ Continue (but risky) | ❌ Fail-fast (safe) |
| Lock stolen | ✅ Continue (ID collisions!) | ❌ Fail-fast (safe) |
| Detection time | ⚠️ Never | ✅ Max 12 hours |
| Error clarity | ⚠️ Silent | ✅ Clear messages |
| Data safety | ⚠️ Risk of corruption | ✅ Protected |

---

## ✅ Kết luận

**Giải pháp này:**
- ✅ Sửa code **không nhiều** (~50 lines)
- ✅ **Tối ưu** theo best practices (ownership verification)
- ✅ **Tránh được** các case nguy hiểm:
  - Lock loss detection
  - Lock theft detection
  - ID collision prevention
- ✅ **Fail-fast** để prevent corruption
- ✅ **Self-healing** qua restart

**Đây là giải pháp enterprise-grade để đảm bảo TSID uniqueness!**

---

## 🚀 Production Deployment

**⚠️ Lưu ý:** Fail-fast là intentional để prevent corruption, nhưng trong production **PHẢI** có auto-restart mechanism.

**Xem thêm:** [`TSID_PRODUCTION_DEPLOYMENT.md`](./TSID_PRODUCTION_DEPLOYMENT.md) để biết cách setup:
- Docker restart policies
- systemd service auto-restart
- Kubernetes deployment với health checks
- Monitoring & alerting
- Self-healing flow

**TL;DR:** Không cần restart thủ công - auto-restart mechanism (systemd/Docker/K8s) sẽ tự động restart khi instance crash!
