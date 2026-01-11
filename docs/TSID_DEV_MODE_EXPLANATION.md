# TSID Dev Mode - Giải thích chi tiết

## ❓ Câu hỏi: `allow-dev-mode` là gì?

**TL;DR:**
- `allow-dev-mode` **KHÔNG phải** "chỉ cho phép TSID chạy trong dev mode"
- `allow-dev-mode` là **"cho phép fallback behavior khi Redis không có"**
- TSID **LUÔN chạy** trong cả dev và production
- Khác biệt: **Cách xử lý khi Redis unavailable**

---

## 🔍 Giải thích chi tiết

### 1. `allow-dev-mode` là gì?

```java
@Value("${tsid.allow-dev-mode:false}")  // Default: false
private boolean allowDevMode;
```

**Ý nghĩa:**
- `true`: Cho phép **fallback** (dùng random node ID) nếu Redis không có
- `false`: **Fail-fast** (throw exception) nếu Redis không có

**⚠️ QUAN TRỌNG:**
- Đây **KHÔNG phải** flag để bật/tắt TSID
- TSID **LUÔN chạy** trong cả dev và production
- Đây chỉ là flag để quyết định **behavior khi Redis unavailable**

---

## 📊 So sánh Behavior

### Scenario: Redis không có hoặc không kết nối được

#### ❌ Production Mode (`allow-dev-mode: false`)

```yaml
# application-prod.yaml
tsid:
  allow-dev-mode: false  # ← Production: Fail-fast
```

**Behavior:**
```java
if (redisTemplate == null) {
    if (allowDevMode) {
        // ❌ Không vào đây (vì allowDevMode = false)
    } else {
        // ✅ VÀO ĐÂY: Fail-fast
        throw new IllegalStateException(
            "Cannot allocate TSID nodeId – Redis unavailable. " +
            "TSID requires Redis for distributed node allocation."
        );
    }
}
```

**Kết quả:**
- ❌ **App KHÔNG start được** (throw exception)
- ✅ **An toàn**: Không có risk ID collision
- ✅ **Rõ ràng**: Error message rõ ràng, dễ debug

**Tại sao?**
- Production có nhiều instances → Cần Redis để allocate unique Node ID
- Nếu không có Redis → Tất cả instances sẽ dùng cùng Node ID → **ID collision!**
- Fail-fast ngăn chặn vấn đề này

---

#### ✅ Development Mode (`allow-dev-mode: true`)

```yaml
# application-dev.yaml
tsid:
  allow-dev-mode: true  # ← Dev: Cho phép fallback
```

**Behavior:**
```java
if (redisTemplate == null) {
    if (allowDevMode) {
        // ✅ VÀO ĐÂY: Fallback với random Node ID
        logger.warn("⚠️  DEV MODE: Redis not available - using random Node ID. " +
                   "TSID uniqueness NOT guaranteed in multi-instance deployments!");
        int randomNodeId = (int) (Math.random() * MAX_NODE_ID);
        logger.info("Using random Node ID: {} (dev mode)", randomNodeId);
        return randomNodeId;
    } else {
        // ❌ Không vào đây
    }
}
```

**Kết quả:**
- ✅ **App vẫn start được** (dùng random Node ID)
- ⚠️ **Warning**: Log cảnh báo về uniqueness
- ✅ **Tiện lợi**: Developer không cần Redis để code local

**Tại sao OK trong dev?**
- Dev thường chỉ chạy **1 instance** → Không có collision risk
- Developer có thể code mà không cần setup Redis
- Random Node ID đủ cho development

---

## 🔄 Flow Diagram

### Production Mode (`allow-dev-mode: false`)

```
App Startup
    │
    ▼
Redis available?
    │
    ├─ YES → Allocate Node ID từ Redis ✅
    │         → App start thành công
    │
    └─ NO → Throw IllegalStateException ❌
            → App KHÔNG start được
            → Error: "Redis unavailable - TSID requires Redis"
```

### Development Mode (`allow-dev-mode: true`)

```
App Startup
    │
    ▼
Redis available?
    │
    ├─ YES → Allocate Node ID từ Redis ✅
    │         → App start thành công
    │
    └─ NO → Use random Node ID (0-1023) ⚠️
            → Log warning về uniqueness
            → App vẫn start được
            → OK cho dev (1 instance)
```

---

## 💡 Ví dụ thực tế

### Scenario 1: Developer mới join team

**Setup:**
```bash
# Developer chưa cài Redis
# Chỉ muốn code và test local
```

**Config:**
```yaml
# application-dev.yaml
tsid:
  allow-dev-mode: true  # ← Cho phép chạy không cần Redis
```

**Kết quả:**
```
✅ App start thành công
⚠️  Warning: "DEV MODE: Redis not available - using random Node ID"
✅ Developer có thể code và test ngay
```

---

### Scenario 2: Production deployment

**Setup:**
```bash
# Production có 3 instances
# Redis cluster đang down (maintenance)
```

**Config:**
```yaml
# application-prod.yaml
tsid:
  allow-dev-mode: false  # ← Fail-fast
```

**Kết quả:**
```
❌ Instance 1: Throw exception → Không start
❌ Instance 2: Throw exception → Không start
❌ Instance 3: Throw exception → Không start

✅ An toàn: Không có instance nào chạy với duplicate Node ID
✅ Error rõ ràng: "Redis unavailable - TSID requires Redis"
```

**Nếu dùng `allow-dev-mode: true` trong production:**
```
⚠️  Instance 1: Random Node ID = 123
⚠️  Instance 2: Random Node ID = 456
⚠️  Instance 3: Random Node ID = 123  ← Có thể trùng!

❌ NGUY HIỂM: Instance 1 và 3 có thể có cùng Node ID
❌ → ID collision → Data corruption!
```

---

## 📋 Code Logic Chi Tiết

### Phần quan trọng trong `TsidConfig.allocateNodeId()`:

```java
private int allocateNodeId() {
    // ────────────────────────────────────────────────────────────────
    // STEP 1: Check Redis availability
    // ────────────────────────────────────────────────────────────────
    if (redisTemplate == null) {
        
        // ────────────────────────────────────────────────────────────
        // DEV MODE: Cho phép fallback với random Node ID
        // ────────────────────────────────────────────────────────────
        if (allowDevMode) {
            logger.warn("⚠️  DEV MODE: Redis not available - using random Node ID. " +
                       "TSID uniqueness NOT guaranteed in multi-instance deployments!");
            
            // Random Node ID (0-1023)
            int randomNodeId = (int) (Math.random() * MAX_NODE_ID);
            logger.info("Using random Node ID: {} (dev mode)", randomNodeId);
            return randomNodeId;  // ← Return ngay, không cần Redis
        }
        
        // ────────────────────────────────────────────────────────────
        // PRODUCTION MODE: Fail-fast - Redis là REQUIRED
        // ────────────────────────────────────────────────────────────
        else {
            throw new IllegalStateException(
                "Cannot allocate TSID nodeId – Redis unavailable. " +
                "TSID requires Redis for distributed node allocation. " +
                "Set tsid.allow-dev-mode=true for development " +
                "(NOT recommended for production)."
            );
        }
    }
    
    // ────────────────────────────────────────────────────────────────
    // STEP 2: Redis available → Allocate Node ID từ Redis
    // ────────────────────────────────────────────────────────────────
    try {
        for (int i = 0; i < MAX_NODE_ID; i++) {
            String key = NODE_KEY_PREFIX + i;
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, "LOCKED", Duration.ofHours(24));
            
            if (Boolean.TRUE.equals(acquired)) {
                allocatedNodeKey.set(key);
                logger.info("Successfully allocated TSID Node ID: {} from Redis", i);
                return i;  // ← Return Node ID từ Redis
            }
        }
        
        // All Node IDs taken
        throw new IllegalStateException("All Node IDs are in use");
        
    } catch (RedisConnectionFailureException e) {
        // Redis connection failed
        if (allowDevMode) {
            // Dev mode: Fallback với random
            logger.warn("⚠️  DEV MODE: Redis connection failed - using random Node ID");
            return (int) (Math.random() * MAX_NODE_ID);
        } else {
            // Production: Fail-fast
            throw new IllegalStateException("Redis connection failed", e);
        }
    }
}
```

---

## 🎯 Tóm tắt

| Mode | `allow-dev-mode` | Redis Unavailable | Behavior |
|------|------------------|-------------------|----------|
| **Dev** | `true` | ✅ App vẫn start | Dùng random Node ID + Warning |
| **Prod** | `false` | ❌ App KHÔNG start | Throw exception (Fail-fast) |

### Key Points:

1. ✅ **TSID LUÔN chạy** trong cả dev và production
2. ✅ **`allow-dev-mode`** chỉ ảnh hưởng behavior khi Redis unavailable
3. ✅ **Dev mode**: Cho phép fallback (tiện cho developer)
4. ✅ **Production mode**: Fail-fast (an toàn, tránh collision)

### Best Practice:

```yaml
# ✅ Development
tsid:
  allow-dev-mode: true   # OK - chỉ 1 instance local

# ✅ Production
tsid:
  allow-dev-mode: false  # MUST - nhiều instances, cần Redis
```

---

## ❓ FAQ

### Q: Tại sao không luôn dùng random Node ID?

**A:** Vì trong production có nhiều instances:
- Instance 1: Random = 123
- Instance 2: Random = 456
- Instance 3: Random = 123 ← **Trùng với Instance 1!**
- → ID collision → Data corruption!

### Q: Tại sao dev mode lại OK với random?

**A:** Vì dev thường chỉ chạy 1 instance:
- Chỉ có 1 instance → Không có collision risk
- Random Node ID đủ cho development

### Q: Có thể set `allow-dev-mode: true` trong production không?

**A:** ❌ **KHÔNG NÊN!**
- Risk ID collision khi có nhiều instances
- Fail-fast trong production là đúng để đảm bảo an toàn

### Q: TSID có chạy khi `allow-dev-mode: false` không?

**A:** ✅ **CÓ!** TSID luôn chạy.
- `allow-dev-mode` chỉ ảnh hưởng behavior khi Redis unavailable
- Nếu Redis available → TSID chạy bình thường với Node ID từ Redis

---

## 📝 Kết luận

**`allow-dev-mode` không phải là flag để bật/tắt TSID**, mà là flag để quyết định:
- **Dev mode (`true`)**: Cho phép fallback khi Redis không có (tiện cho developer)
- **Production mode (`false`)**: Fail-fast khi Redis không có (an toàn cho production)

TSID **LUÔN chạy** trong cả 2 mode - chỉ khác cách xử lý khi Redis unavailable! 🎯
