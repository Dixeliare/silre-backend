# TSID Health Indicator - Endpoint Guide

## ✅ TsidHealthIndicator đã HOÀN THIỆN!

`TsidHealthIndicator` là một **Spring Boot Actuator Health Indicator** hoàn chỉnh, tự động expose qua endpoint `/actuator/health`.

---

## 🔍 Cách hoạt động

### 1. **Auto-Registration**

```java
@Component  // ← Spring tự động scan và register
public class TsidHealthIndicator implements HealthIndicator {
    // ...
}
```

**Spring Boot tự động:**
- ✅ Scan class có `@Component` và implement `HealthIndicator`
- ✅ Register vào Actuator health system
- ✅ Tự động gọi `health()` method khi có request đến `/actuator/health`

### 2. **Endpoint Configuration**

```yaml
# application.yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info  # ← Expose health endpoint
  endpoint:
    health:
      show-details: when-authorized  # ← Show details
```

### 3. **Security Configuration**

```java
// SecurityConfig.java
.requestMatchers("/actuator/**").permitAll()  // ← Cho phép public access
```

---

## 🌐 Cách truy cập

### 1. **Via Browser**

```
http://localhost:8080/actuator/health
```

### 2. **Via curl**

```bash
# Basic health check
curl http://localhost:8080/actuator/health

# Pretty JSON
curl http://localhost:8080/actuator/health | python3 -m json.tool
```

### 3. **Via Swagger UI**

Swagger UI có thể show Actuator endpoints nếu config:
```yaml
springdoc:
  show-actuator: true
```

---

## 📊 Example Response

### Full Health Response (with details):

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
        "threshold": 10485760,
        "exists": true
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

### TSID Component Only:

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
    }
  }
}
```

### With Warning (Clock Sync Issue):

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
        "nodeCapacity": "1024 instances",
        "warning": "Clock synchronization issue detected. Time difference: 1577836800000 ms. Ensure NTP is configured correctly. TSID uniqueness may be compromised."
      }
    }
  }
}
```

### Down Status (Factory Not Initialized):

```json
{
  "status": "DOWN",
  "components": {
    "tsid": {
      "status": "DOWN",
      "details": {
        "status": "TSID Factory not initialized",
        "error": "Factory injection failed"
      }
    }
  }
}
```

---

## 🔍 Health Checks Performed

### 1. **Factory Initialization Check**

```java
if (tsidFactory == null) {
    return Health.down().withDetails(...);
}
```

**Checks:** TsidFactory có được inject không?

---

### 2. **TSID Generation Test**

```java
var testTsid = tsidFactory.create();
long tsidValue = testTsid.toLong();
```

**Checks:** Factory có generate TSID được không?

---

### 3. **Clock Synchronization Check**

```java
long currentTime = System.currentTimeMillis();
long tsidTime = extractTimestamp(tsidValue);
long timeDiff = Math.abs(currentTime - tsidTime);

if (timeDiff > 1000) {
    // Warning: Clock drift > 1 second
}
```

**Checks:** System clock có sync không? (NTP configured?)

---

### 4. **Node ID Allocation Check**

```java
if (allocatedNodeId.get() < 0) {
    // Warning: Node ID not properly allocated
}
```

**Checks:** Node ID có được allocate đúng không?

---

## 🎯 Response Fields Explained

| Field | Type | Description |
|-------|------|-------------|
| `status` | String | "TSID system operational" or error message |
| `nodeId` | Integer | Allocated Node ID (0-1023) |
| `testTsid` | Long | Test TSID generated during health check |
| `timestamp` | String | Current UTC timestamp |
| `maxNodeId` | Integer | Maximum Node ID (1023) |
| `nodeCapacity` | String | "1024 instances" |
| `warning` | String | Optional warning message (clock sync, node ID) |

---

## 🧪 Testing

### Test 1: Basic Health Check

```bash
curl http://localhost:8080/actuator/health
```

**Expected:** Status "UP" với TSID component

---

### Test 2: Check TSID Component Only

```bash
curl http://localhost:8080/actuator/health | jq '.components.tsid'
```

**Expected:**
```json
{
  "status": "UP",
  "details": {
    "status": "TSID system operational",
    "nodeId": 0,
    "testTsid": 798197243508169067,
    ...
  }
}
```

---

### Test 3: Check for Warnings

```bash
curl http://localhost:8080/actuator/health | jq '.components.tsid.details.warning'
```

**Expected:** `null` (no warnings) hoặc warning message

---

## 🔧 Configuration

### Enable/Disable Health Details

```yaml
management:
  endpoint:
    health:
      show-details: always  # Always show details
      # show-details: when-authorized  # Only for authorized users
      # show-details: never  # Never show details
```

### Expose Only Health Endpoint

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health  # Only health endpoint
```

### Custom Health Endpoint Path

```yaml
management:
  endpoints:
    web:
      base-path: /actuator  # Default
      # base-path: /monitoring  # Custom path
```

---

## 📝 Summary

✅ **TsidHealthIndicator đã HOÀN THIỆN:**

1. ✅ Implement `HealthIndicator` interface
2. ✅ Auto-register với `@Component`
3. ✅ Expose qua `/actuator/health` endpoint
4. ✅ Perform comprehensive health checks
5. ✅ Return detailed status information
6. ✅ Warn about potential issues (clock sync, node ID)

✅ **Cách truy cập:**
- Browser: `http://localhost:8080/actuator/health`
- curl: `curl http://localhost:8080/actuator/health`
- Swagger UI: Nếu `show-actuator: true`

✅ **Response format:**
- Status: "UP" or "DOWN"
- Details: Node ID, test TSID, timestamp, warnings

---

## 🎓 Spring Boot Actuator Integration

```
GET /actuator/health
    │
    ▼
Spring Actuator HealthEndpoint
    │
    ├─ Collect all HealthIndicators
    │   └─ TsidHealthIndicator (auto-discovered)
    │
    ├─ Call health() method on each indicator
    │   └─ TsidHealthIndicator.health()
    │       ├─ Check factory
    │       ├─ Test TSID generation
    │       ├─ Check clock sync
    │       └─ Return Health object
    │
    └─ Aggregate results
        └─ Return JSON response
```

**Key Point:** Spring Boot tự động discover và call `TsidHealthIndicator.health()` khi có request đến `/actuator/health`!
