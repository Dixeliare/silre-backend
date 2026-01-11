# Timezone Detection - Implementation Guide

## 📋 Tổng quan

Hệ thống tự động detect timezone của user từ HTTP request headers và sử dụng nó để tạo timestamps chính xác theo timezone của user.

---

## 🏗️ Kiến trúc

```
HTTP Request
    │
    ▼
TimezoneInterceptor (preHandle)
    │
    ├─ Extract X-Timezone header
    │
    ▼
TimezoneContext.setTimezone()
    │
    ▼
Entity @PrePersist/@PreUpdate
    │
    ├─ TimezoneContext.getCurrentTimezone()
    │
    ▼
OffsetDateTime.now(zoneId)
    │
    ▼
Database (stored with timezone offset)
```

---

## 🔧 Components

### 1. `TimezoneContext.java` - Thread-Local Storage

**Mục đích:** Lưu timezone của user trong thread-local để có thể truy cập từ bất kỳ đâu trong request.

```java
// Set timezone
TimezoneContext.setTimezone("Asia/Ho_Chi_Minh");

// Get timezone
ZoneId zoneId = TimezoneContext.getCurrentTimezone();

// Clear after request
TimezoneContext.clear();
```

**Thread-Safe:** Mỗi request có timezone riêng, không conflict giữa các requests.

---

### 2. `TimezoneInterceptor.java` - Request Interceptor

**Mục đích:** Detect timezone từ HTTP headers và set vào `TimezoneContext`.

**Detection Priority:**
1. ✅ **X-Timezone header** (explicit from client)
2. ⏳ **User's stored timezone** (TODO: when authentication added)
3. ✅ **UTC** (default fallback)

**HTTP Header:**
```http
X-Timezone: Asia/Ho_Chi_Minh
```

**Valid Timezone Formats:**
- `Asia/Ho_Chi_Minh`
- `America/New_York`
- `Europe/London`
- `UTC`
- `GMT+7`

---

### 3. `WebMvcConfig.java` - Interceptor Registration

**Mục đích:** Đăng ký `TimezoneInterceptor` với Spring MVC.

**Applied to:**
- ✅ All API endpoints (`/**`)
- ❌ Excluded: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`

---

### 4. Entity Integration - `User.java`

**Mục đích:** Sử dụng detected timezone khi tạo timestamps.

```java
@PrePersist
protected void onCreate() {
    ZoneId zoneId = getEffectiveTimezone();
    createdAt = OffsetDateTime.now(zoneId);
}
```

**Priority Logic:**
1. Request header timezone (from `TimezoneContext`)
2. User's stored timezone preference
3. UTC (default)

---

## 📊 Flow Diagram

### Scenario: User creates a post from Vietnam (GMT+7)

```
1. Frontend sends request:
   POST /api/v1/posts
   Headers:
     X-Timezone: Asia/Ho_Chi_Minh
   
2. TimezoneInterceptor.preHandle():
   - Extracts "Asia/Ho_Chi_Minh" from header
   - Sets TimezoneContext.setTimezone(ZoneId.of("Asia/Ho_Chi_Minh"))
   
3. PostController.createPost():
   - Creates Post entity
   - Calls postRepository.save(post)
   
4. Post entity @PrePersist:
   - Calls getEffectiveTimezone()
   - Returns ZoneId.of("Asia/Ho_Chi_Minh")
   - Creates: OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
   - Result: 2026-01-11T20:30:00+07:00
   
5. Database stores:
   - Timestamp with offset: 2026-01-11T20:30:00+07:00
   
6. TimezoneInterceptor.afterCompletion():
   - Clears TimezoneContext.clear()
```

---

## 💻 Usage Examples

### Frontend (JavaScript)

```javascript
// Get user's timezone
const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
// Example: "Asia/Ho_Chi_Minh"

// Send in request header
fetch('/api/v1/users', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Timezone': timezone  // ← Timezone detection
  },
  body: JSON.stringify({
    email: 'user@example.com',
    displayName: 'John Doe',
    password: 'Password123!'
  })
});
```

### Backend (Service Layer)

```java
@Service
public class PostService {
    
    public Post createPost(CreatePostRequest request) {
        Post post = new Post();
        post.setContent(request.content());
        
        // Timestamp will automatically use detected timezone
        // via @PrePersist hook
        return postRepository.save(post);
    }
}
```

### Manual Timezone Usage

```java
// In service or entity
import com.longdx.silre_backend.util.TimezoneUtils;

// Use detected timezone
OffsetDateTime now = TimezoneUtils.now();

// Use specific timezone
OffsetDateTime now = TimezoneUtils.now("Asia/Ho_Chi_Minh");
```

---

## 🔍 How It Works

### Step 1: Request Arrives

```http
POST /api/v1/users HTTP/1.1
Host: localhost:8080
Content-Type: application/json
X-Timezone: Asia/Ho_Chi_Minh

{
  "email": "user@example.com",
  "displayName": "John Doe",
  "password": "Password123!"
}
```

### Step 2: Interceptor Detects

```java
// TimezoneInterceptor.preHandle()
String timezoneHeader = request.getHeader("X-Timezone");
// → "Asia/Ho_Chi_Minh"

ZoneId zoneId = ZoneId.of(timezoneHeader);
// → ZoneId["Asia/Ho_Chi_Minh"]

TimezoneContext.setTimezone(zoneId);
// → Stored in ThreadLocal
```

### Step 3: Entity Uses Timezone

```java
// User entity @PrePersist
protected void onCreate() {
    ZoneId zoneId = getEffectiveTimezone();
    // → ZoneId["Asia/Ho_Chi_Minh"] (from TimezoneContext)
    
    createdAt = OffsetDateTime.now(zoneId);
    // → 2026-01-11T20:30:00+07:00
}
```

### Step 4: Cleanup

```java
// TimezoneInterceptor.afterCompletion()
TimezoneContext.clear();
// → Removes from ThreadLocal
```

---

## ✅ Best Practices

### 1. Always Store UTC in Database (Recommended)

**Current Implementation:** Stores with timezone offset (OffsetDateTime)

**Alternative (Better for large scale):**
```java
// Store as UTC in database
@Column(name = "created_at")
private Instant createdAt;  // Always UTC

// Convert to user timezone when returning to client
public OffsetDateTime getCreatedAt(ZoneId userTimezone) {
    return createdAt.atZone(userTimezone).toOffsetDateTime();
}
```

### 2. Validate Timezone Strings

```java
try {
    ZoneId zoneId = ZoneId.of(timezoneString);
    // Valid timezone
} catch (Exception e) {
    // Invalid - fallback to UTC
    zoneId = ZoneId.of("UTC");
}
```

### 3. Handle Missing Timezone

- If no `X-Timezone` header → Use UTC
- If invalid timezone → Use UTC
- If user not authenticated → Use UTC

---

## 🧪 Testing

### Test with curl:

```bash
# With timezone header
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -H "X-Timezone: Asia/Ho_Chi_Minh" \
  -d '{
    "email": "test@example.com",
    "displayName": "Test User",
    "password": "Password123!"
  }'

# Without timezone header (uses UTC)
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test2@example.com",
    "displayName": "Test User 2",
    "password": "Password123!"
  }'
```

### Verify in Database:

```sql
SELECT internal_id, email, timezone, created_at 
FROM users 
WHERE email = 'test@example.com';

-- Expected:
-- timezone: "Asia/Ho_Chi_Minh"
-- created_at: 2026-01-11 20:30:00+07:00
```

---

## 🔮 Future Enhancements

### 1. User Profile Timezone

```java
// When user updates profile
user.setTimezone("America/New_York");
userRepository.save(user);

// Future requests from this user will use their stored timezone
// (if X-Timezone header not provided)
```

### 2. IP-Based Detection

```java
// Detect timezone from IP address
String clientIp = request.getRemoteAddr();
ZoneId detectedTimezone = ipToTimezoneService.detect(clientIp);
```

### 3. Browser Timezone Auto-Detection

```javascript
// Frontend automatically sends timezone
const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
// Include in all API requests
```

---

## 📝 Summary

✅ **Implemented:**
- Timezone detection from HTTP header (`X-Timezone`)
- Thread-local storage (`TimezoneContext`)
- Automatic timezone usage in entities
- Fallback to UTC if not detected

⏳ **TODO (Future):**
- Use authenticated user's stored timezone
- IP-based timezone detection
- Timezone validation service

---

## 🎯 Key Points

1. **Timezone được detect từ HTTP header `X-Timezone`**
2. **Lưu trong ThreadLocal** (mỗi request riêng biệt)
3. **Entities tự động dùng timezone** khi tạo timestamps
4. **Fallback về UTC** nếu không detect được
5. **Thread-safe** - không conflict giữa các requests
