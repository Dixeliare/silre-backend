# TSID Redis Key - Giải thích tại sao xóa key không ảnh hưởng

## ❓ Câu hỏi

**"Tại sao sau khi xóa node ID key trong Redis, backend vẫn tạo user được bình thường?"**

## ✅ Câu trả lời ngắn gọn

**Đây là hành vi ĐÚNG và BÌNH THƯỜNG!**

Redis key chỉ là **distributed lock** để allocate Node ID khi startup. Sau khi allocate xong, Node ID được lưu trong **memory (JVM)** và backend không cần Redis key nữa để generate TSID.

---

## 🔍 Giải thích chi tiết

### 1. **Khi nào Node ID được allocate?**

```
Spring Boot Startup
    │
    ▼
TsidConfig.tsidFactory() được gọi (CHỈ MỘT LẦN)
    │
    ▼
allocateNodeId() → Lấy Node ID từ Redis
    │
    ▼
Tạo TsidFactory với Node ID
    │
    ▼
Lưu factory vào memory (JVM)
    │
    ▼
Backend sẵn sàng generate TSID
```

**Key Point:** Node ID chỉ được allocate **MỘT LẦN** khi app startup.

---

### 2. **Node ID được lưu ở đâu?**

#### A. **In Memory (JVM)** - Chính
```java
// Trong TsidIdGenerator.java
private static TsidFactory tsidFactory;  // ← Node ID ở đây!

// Factory đã có Node ID embedded:
TsidFactory factory = TsidFactory.builder()
    .withNode(nodeId)  // ← Node ID được set vào factory
    .build();
```

#### B. **In Redis** - Distributed Lock (phụ)
```redis
sys:tsid:node:0  → "LOCKED" (TTL: 24h)
```

**Mục đích của Redis key:**
- ✅ Prevent conflicts khi allocate (nhiều instance cùng lúc)
- ✅ Distributed lock để đảm bảo unique Node ID
- ❌ **KHÔNG** cần để generate TSID sau khi allocate xong

---

### 3. **Khi nào Node ID được dùng?**

```java
// Khi save entity:
userRepository.save(user);
    │
    ▼
Hibernate gọi TsidIdGenerator.generate()
    │
    ▼
tsidFactory.create()  // ← Dùng Node ID từ MEMORY
    │
    ▼
Generate TSID với Node ID đã có sẵn
```

**Key Point:** Node ID được lấy từ **memory**, không phải từ Redis!

---

## 📊 Timeline Example

### Scenario: Xóa Redis key sau khi backend đã start

```
00:00 - Backend starts
        ├─ allocateNodeId() → Lấy Node ID 0 từ Redis
        ├─ Tạo TsidFactory với Node ID 0
        ├─ Lưu factory vào memory
        └─ Redis key: sys:tsid:node:0 (TTL: 24h)

00:05 - User tạo user mới
        ├─ TsidIdGenerator.generate()
        ├─ tsidFactory.create() → Dùng Node ID 0 từ MEMORY
        └─ ✅ Generate TSID thành công

00:10 - Bạn xóa Redis key: sys:tsid:node:0
        └─ Key bị xóa khỏi Redis

00:15 - User tạo user mới
        ├─ TsidIdGenerator.generate()
        ├─ tsidFactory.create() → Vẫn dùng Node ID 0 từ MEMORY
        └─ ✅ Generate TSID thành công (KHÔNG ẢNH HƯỞNG!)

00:20 - Scheduled refresh chạy
        ├─ refreshTsidLock() cố refresh key
        └─ ⚠️ Fail (key không tồn tại) → Log warning, nhưng KHÔNG crash
```

**Kết luận:** Xóa Redis key **KHÔNG ẢNH HƯỞNG** đến việc generate TSID!

---

## ⚠️ Khi nào Redis key quan trọng?

### 1. **Khi Backend Startup (Allocate Node ID)**

```java
// TsidConfig.tsidFactory() chạy khi startup
int nodeId = allocateNodeId();  // ← CẦN Redis key ở đây!

// Nếu Redis key không tồn tại:
// → allocateNodeId() sẽ allocate Node ID mới
// → Tạo key mới trong Redis
```

### 2. **Khi Scheduled Refresh (Keep-Alive)**

```java
@Scheduled(fixedDelayString = "PT12H")
public void refreshTsidLock() {
    // Cố refresh TTL của key
    // Nếu key không tồn tại → Fail, nhưng KHÔNG crash
}
```

**Impact:** 
- ✅ App vẫn chạy bình thường
- ⚠️ Key sẽ không được refresh
- ⚠️ Nếu backend restart, có thể allocate Node ID khác

---

## 🎯 Kết luận

### ✅ **Hành vi hiện tại là ĐÚNG:**

1. **Redis key chỉ cần khi allocate Node ID** (startup)
2. **Sau khi allocate, Node ID lưu trong memory**
3. **Generate TSID không cần Redis key**
4. **Xóa key không ảnh hưởng đến việc generate TSID**

### ⚠️ **Lưu ý:**

1. **Nếu xóa key và backend restart:**
   - Backend sẽ allocate Node ID mới (có thể khác)
   - Không ảnh hưởng đến IDs đã generate trước đó
   - Nhưng có thể conflict nếu nhiều instance cùng allocate

2. **Scheduled refresh sẽ fail:**
   - Không crash app
   - Chỉ log warning
   - Key sẽ không được refresh

3. **Best Practice:**
   - ✅ Đừng xóa Redis keys khi backend đang chạy
   - ✅ Để scheduled refresh tự động maintain keys
   - ✅ Chỉ cleanup stale keys (TTL > 20h)

---

## 🔧 Test để verify

### Test 1: Xóa key khi backend đang chạy

```bash
# 1. Backend đang chạy với Node ID 0
redis-cli GET "sys:tsid:node:0"
# Output: "LOCKED"

# 2. Xóa key
redis-cli DEL "sys:tsid:node:0"

# 3. Tạo user mới qua Swagger
# → ✅ Vẫn tạo được bình thường!

# 4. Check logs
# → Scheduled refresh sẽ log warning (key không tồn tại)
```

### Test 2: Restart backend sau khi xóa key

```bash
# 1. Xóa key
redis-cli DEL "sys:tsid:node:0"

# 2. Restart backend
# → Backend sẽ allocate Node ID mới (có thể là 0 hoặc khác)

# 3. Check Redis
redis-cli GET "sys:tsid:node:0"
# Output: "LOCKED" (key mới được tạo)
```

---

## 📝 Summary

| Hành động | Ảnh hưởng đến Generate TSID? | Ảnh hưởng đến gì? |
|-----------|------------------------------|-------------------|
| Xóa Redis key khi backend đang chạy | ❌ **KHÔNG** | ⚠️ Scheduled refresh sẽ fail |
| Xóa Redis key và restart backend | ❌ **KHÔNG** | ⚠️ Backend sẽ allocate Node ID mới |
| Redis down khi backend đang chạy | ❌ **KHÔNG** | ⚠️ Scheduled refresh sẽ fail |
| Redis down khi backend startup | ✅ **CÓ** | ❌ Backend sẽ fail-fast (production) hoặc dùng random Node ID (dev mode) |

**Key Takeaway:**
- Redis key = Distributed lock để allocate Node ID
- Node ID = Lưu trong memory sau khi allocate
- Generate TSID = Dùng Node ID từ memory, không cần Redis

---

## 🎓 Architecture Insight

```
┌─────────────────────────────────────────────────────────┐
│                    Backend Startup                       │
├─────────────────────────────────────────────────────────┤
│  1. allocateNodeId()                                    │
│     └─ Redis: SETNX sys:tsid:node:0 "LOCKED"            │
│                                                          │
│  2. Create TsidFactory with Node ID                     │
│     └─ Factory embedded với Node ID                    │
│                                                          │
│  3. Store factory in memory (JVM)                       │
│     └─ TsidIdGenerator.tsidFactory = factory            │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│              Generate TSID (Runtime)                    │
├─────────────────────────────────────────────────────────┤
│  userRepository.save(user)                              │
│    │                                                     │
│    └─ TsidIdGenerator.generate()                        │
│         │                                                │
│         └─ tsidFactory.create()                         │
│              │                                            │
│              └─ Dùng Node ID từ MEMORY (KHÔNG CẦN Redis)│
└─────────────────────────────────────────────────────────┘
```

**Redis key chỉ cần khi allocate, không cần khi generate!**
