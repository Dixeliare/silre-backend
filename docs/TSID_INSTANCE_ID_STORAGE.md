# TSID Instance ID Storage - Memory vs Redis

## 📍 Vị trí lưu trữ `instanceId`

### 1. **Memory (Java Object)**

```java
// File: TsidConfig.java
// Line: 178

private final String instanceId = generateInstanceId();
// Value: "instance:mbp-cua-longdx:86240:1768209632390"
```

**Vị trí:** Field trong `TsidConfig` class (Java heap memory)

**Mục đích:** 
- Giữ instanceId để verify ownership khi refresh
- So sánh với value trong Redis

**Lifecycle:**
- Generated khi `TsidConfig` constructor chạy (app startup)
- Tồn tại trong memory suốt thời gian app chạy
- Mất khi app shutdown

---

### 2. **Redis (Distributed Storage)**

```java
// File: TsidConfig.java
// Line: 422

redisTemplate.opsForValue().setIfAbsent(key, instanceId, Duration.ofHours(24));
```

**Cấu trúc Redis:**

```
KEY:   "sys:tsid:node:0"
VALUE: "instance:mbp-cua-longdx:86240:1768209632390"
TTL:   24 hours (86400 seconds)
```

**Mục đích:**
- Mark Node ID đã được allocate bởi instance nào
- Verify ownership khi refresh lock
- Detect lock theft (nếu value khác instanceId trong memory)

**Lifecycle:**
- Created khi acquire lock (startup)
- Refreshed mỗi 12 giờ (extend TTL)
- Deleted khi:
  - TTL expires (24 giờ không refresh)
  - Redis restart (nếu không persistence)
  - Manual delete

---

## 🔄 Flow: Memory ↔ Redis

### Startup Flow:

```
1. TsidConfig constructor
   └─ Generate instanceId
      └─ Store in MEMORY (field)
         Value: "instance:mbp-cua-longdx:86240:1768209632390"

2. allocateNodeId()
   └─ SETNX Redis key
      KEY:   "sys:tsid:node:0"
      VALUE: "instance:mbp-cua-longdx:86240:1768209632390"  ← Copy từ memory
      TTL:   24 hours
```

**Kết quả:**
- ✅ Memory: `instanceId` field
- ✅ Redis: `sys:tsid:node:0` = `instanceId`

---

### Refresh Flow (Every 12 hours):

```
1. refreshTsidLock()
   │
   ├─ Get key from memory: "sys:tsid:node:0"
   │
   ├─ Get value from Redis: "instance:mbp-cua-longdx:86240:1768209632390"
   │
   ├─ Compare:
   │   Memory instanceId: "instance:mbp-cua-longdx:86240:1768209632390"
   │   Redis value:        "instance:mbp-cua-longdx:86240:1768209632390"
   │   └─ Match? → Continue
   │   └─ Different? → FAIL-FAST (lock stolen!)
   │   └─ null? → FAIL-FAST (lock lost!)
   │
   └─ Refresh TTL (24 hours)
```

---

## 🎯 Tại sao cần cả hai?

### Memory (instanceId field):

**Ưu điểm:**
- ✅ Fast access (không cần network call)
- ✅ Always available (không phụ thuộc Redis)
- ✅ Dùng để compare với Redis value

**Nhược điểm:**
- ❌ Chỉ có trong instance hiện tại
- ❌ Không share được với instance khác

---

### Redis (key-value):

**Ưu điểm:**
- ✅ Shared across instances (distributed)
- ✅ Persistent (nếu Redis có persistence)
- ✅ Atomic operations (SETNX)
- ✅ Auto-expire (TTL)

**Nhược điểm:**
- ❌ Network latency
- ❌ Phụ thuộc Redis availability

---

## 🔍 Verification Logic

### Case 1: Normal Operation

```
Memory:  instanceId = "instance:A:pid1:time1"
Redis:   sys:tsid:node:0 = "instance:A:pid1:time1"

Refresh:
  → Get Redis value: "instance:A:pid1:time1"
  → Compare với memory: Match ✅
  → Refresh TTL
```

**Kết quả:** ✅ Lock valid, continue

---

### Case 2: Lock Lost (Redis restart)

```
Memory:  instanceId = "instance:A:pid1:time1"
Redis:   sys:tsid:node:0 = null (key không tồn tại)

Refresh:
  → Get Redis value: null
  → Compare: null != memory instanceId
  → FAIL-FAST ❌
```

**Kết quả:** ❌ Lock lost, app crashes

---

### Case 3: Lock Stolen

```
Memory:  instanceId = "instance:A:pid1:time1"
Redis:   sys:tsid:node:0 = "instance:B:pid2:time2" (instance khác acquire)

Refresh:
  → Get Redis value: "instance:B:pid2:time2"
  → Compare với memory: "instance:B:pid2:time2" != "instance:A:pid1:time1"
  → FAIL-FAST ❌
```

**Kết quả:** ❌ Lock stolen, app crashes

---

## 📊 Redis Commands để Check

### 1. Check key exists và value:

```bash
redis-cli GET "sys:tsid:node:0"
# Output: "instance:mbp-cua-longdx:86240:1768209632390"
```

---

### 2. Check TTL:

```bash
redis-cli TTL "sys:tsid:node:0"
# Output: 86400 (seconds) = 24 hours
```

---

### 3. Check all TSID node keys:

```bash
redis-cli KEYS "sys:tsid:node:*"
# Output:
# 1) "sys:tsid:node:0"
# 2) "sys:tsid:node:1"
# ...
```

---

### 4. Check value của một key cụ thể:

```bash
redis-cli GET "sys:tsid:node:0"
# Output: "instance:mbp-cua-longdx:86240:1768209632390"
```

---

## 🎓 Summary

### ✅ **instanceId được lưu ở đâu?**

1. **Memory:** Field trong `TsidConfig` class
   - Format: `"instance:{hostname}:{pid}:{timestamp}"`
   - Purpose: Verify ownership khi refresh

2. **Redis:** Value của key `sys:tsid:node:{nodeId}`
   - Format: `"instance:{hostname}:{pid}:{timestamp}"`
   - Purpose: Mark Node ID được allocate bởi instance nào

### ✅ **Cấu trúc Redis:**

```
KEY:   "sys:tsid:node:0"
VALUE: "instance:mbp-cua-longdx:86240:1768209632390"
TTL:   24 hours
```

### ✅ **Tại sao cần cả hai?**

- **Memory:** Fast access, dùng để compare
- **Redis:** Distributed storage, verify ownership

**Cả hai phải match** → Lock valid ✅  
**Không match** → Lock lost/stolen → FAIL-FAST ❌
