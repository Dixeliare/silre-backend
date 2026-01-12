# Actuator Health Endpoint - Troubleshooting

## ❓ Vấn đề: Response chỉ có ít thông tin

### Response hiện tại:
```json
{
  "groups": ["liveness", "readiness"],
  "status": "UP"
}
```

**Vấn đề:** Không có `components` với TSID health details!

---

## ✅ Giải pháp

### 1. **Config `show-details: always`**

**Vấn đề:** Config hiện tại:
```yaml
management:
  endpoint:
    health:
      show-details: when-authorized  # ← Chỉ show khi authenticated
```

**Giải pháp:** Thay đổi thành:
```yaml
management:
  endpoint:
    health:
      show-details: always  # ← Show details luôn
```

### 2. **Restart Backend**

Sau khi thay đổi config, **PHẢI restart backend** để config có hiệu lực!

```bash
# Stop backend
# Start lại backend
```

### 3. **Test lại**

```bash
# Dùng single quotes để tránh zsh error với ?
curl 'http://localhost:8080/actuator/health'

# Hoặc escape
curl "http://localhost:8080/actuator/health?show-details=always"
```

---

## 🔧 Configuration Options

### Option 1: Always Show Details (Development)

```yaml
# application-dev.yaml
management:
  endpoint:
    health:
      show-details: always
```

**Use case:** Local development, debugging

---

### Option 2: Show When Authorized (Production)

```yaml
# application-prod.yaml
management:
  endpoint:
    health:
      show-details: when-authorized
```

**Use case:** Production - chỉ show cho authenticated users

---

### Option 3: Never Show Details

```yaml
management:
  endpoint:
    health:
      show-details: never
```

**Use case:** Security - không expose internal details

---

## 📊 Expected Response (After Fix)

### With `show-details: always`:

```json
{
  "status": "UP",
  "components": {
    "tsid": {
      "status": "UP",
      "details": {
        "status": "TSID system operational",
        "nodeId": 0,
        "testTsid": 798197243508169067,
        "timestamp": "2026-01-11T14:30:52.640653Z",
        "maxNodeId": 1023,
        "nodeCapacity": "1024 instances"
      }
    },
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500107862016,
        "free": 123456789,
        "threshold": 10485760
      }
    },
    "ping": {
      "status": "UP"
    }
  },
  "groups": ["liveness", "readiness"]
}
```

---

## 🐛 Common Issues

### Issue 1: zsh "no matches found" Error

**Error:**
```bash
zsh: no matches found: http://localhost:8080/actuator/health?show-details=always
```

**Cause:** zsh interprets `?` as a glob pattern

**Solution:**
```bash
# Use single quotes
curl 'http://localhost:8080/actuator/health?show-details=always'

# Or escape
curl "http://localhost:8080/actuator/health\?show-details=always"

# Or use --data-urlencode
curl --data-urlencode "show-details=always" http://localhost:8080/actuator/health
```

---

### Issue 2: Response Still Empty After Config Change

**Cause:** Backend chưa restart

**Solution:**
1. Stop backend
2. Start lại backend
3. Test lại endpoint

---

### Issue 3: Components Not Showing

**Possible causes:**
1. `show-details` config chưa đúng
2. Backend chưa restart
3. HealthIndicator chưa được register

**Check:**
```bash
# Check if TsidHealthIndicator is registered
# Look for log: "TSID Factory injected into TsidIdGenerator"

# Check config
grep -r "show-details" src/main/resources/
```

---

## 🧪 Testing Steps

### Step 1: Update Config

```yaml
# application-dev.yaml
management:
  endpoint:
    health:
      show-details: always
```

### Step 2: Restart Backend

```bash
# Stop và start lại
```

### Step 3: Test Endpoint

```bash
# Basic test
curl 'http://localhost:8080/actuator/health'

# Check TSID component specifically
curl 'http://localhost:8080/actuator/health' | jq '.components.tsid'
```

### Step 4: Verify Response

```json
{
  "status": "UP",
  "components": {
    "tsid": {
      "status": "UP",
      "details": {
        "nodeId": 0,
        "testTsid": 798197243508169067,
        ...
      }
    }
  }
}
```

---

## 📝 Summary

**Vấn đề:** Response chỉ có `groups` và `status`, không có `components`

**Nguyên nhân:** 
- `show-details: when-authorized` - cần authentication
- Backend chưa restart sau khi config

**Giải pháp:**
1. ✅ Thay đổi `show-details: always` (dev mode)
2. ✅ Restart backend
3. ✅ Test lại endpoint

**Files đã update:**
- `application.yaml` - Changed to `always`
- `application-dev.yaml` - Added health config
