# TSID FAQ - Câu hỏi thường gặp

## 1. Tại sao hàm `generate()` có `session` và `object` nhưng không dùng?

### Câu trả lời ngắn gọn:
- `session` và `object` là **bắt buộc** trong interface `IdentifierGenerator` của Hibernate
- TSID **không cần** dùng chúng vì TSID độc lập với entity data
- Một số generator khác **cần** dùng chúng (ví dụ: UUID từ entity field)

### Giải thích chi tiết:

#### Interface `IdentifierGenerator` của Hibernate:

```java
public interface IdentifierGenerator {
    Serializable generate(
        SharedSessionContractImplementor session,  // ← Bắt buộc trong interface
        Object object                              // ← Bắt buộc trong interface
    );
}
```

**Tại sao Hibernate yêu cầu 2 tham số này?**

1. **`session` (Hibernate Session):**
   - Cho phép generator truy cập database nếu cần
   - Ví dụ: Generator có thể query database để lấy sequence number
   - Ví dụ: Generator có thể check existing IDs để tránh collision

2. **`object` (Entity đang được save):**
   - Cho phép generator đọc data từ entity
   - Ví dụ: Generator có thể tạo ID dựa trên entity's field values
   - Ví dụ: UUID generator có thể lấy UUID từ entity field nếu có

#### TSID Generator - Tại sao không dùng?

```java
@Override
public Serializable generate(SharedSessionContractImplementor session, Object object) {
    // ❌ KHÔNG CẦN session - TSID không query database
    // ❌ KHÔNG CẦN object - TSID không đọc entity data
    
    // ✅ CHỈ CẦN: tsidFactory.create() - tạo ID độc lập
    return tsidFactory.create().toLong();
}
```

**TSID Generator:**
- ✅ **Độc lập**: TSID chỉ cần timestamp + node ID + sequence
- ✅ **Không query DB**: Không cần check existing IDs
- ✅ **Không đọc entity**: Không cần entity data để tạo ID
- ✅ **Stateless**: Chỉ cần factory (đã inject sẵn)

#### So sánh với Generator khác (CẦN dùng session/object):

**Ví dụ 1: Sequence Generator (CẦN session):**
```java
@Override
public Serializable generate(SharedSessionContractImplementor session, Object object) {
    // ✅ CẦN session để query database sequence
    String sql = "SELECT nextval('user_id_seq')";
    Long nextId = session.createNativeQuery(sql, Long.class).getSingleResult();
    return nextId;
}
```

**Ví dụ 2: UUID từ Entity Field (CẦN object):**
```java
@Override
public Serializable generate(SharedSessionContractImplementor session, Object object) {
    // ✅ CẦN object để đọc UUID từ entity field
    User user = (User) object;
    if (user.getUuid() != null) {
        return user.getUuid();  // Dùng UUID có sẵn
    }
    return UUID.randomUUID();  // Tạo mới nếu chưa có
}
```

**Ví dụ 3: Composite ID từ Entity Fields (CẦN object):**
```java
@Override
public Serializable generate(SharedSessionContractImplementor session, Object object) {
    // ✅ CẦN object để đọc composite key components
    OrderItem item = (OrderItem) object;
    return new OrderItemId(
        item.getOrderId(),    // Từ entity
        item.getProductId()   // Từ entity
    );
}
```

### Kết luận:

- **Interface requirement**: `session` và `object` là bắt buộc trong interface
- **TSID không cần**: TSID độc lập với entity và database
- **Không phải bug**: Đây là design đúng - chỉ implement những gì cần thiết
- **Best practice**: Không dùng tham số không cần thiết (clean code)

---

## 2. `TsidHealthIndicator` được dùng ở đâu và khi nào?

### Câu trả lời ngắn gọn:
- **Được dùng bởi**: Spring Boot Actuator
- **Khi nào**: Khi có request đến `/actuator/health`
- **Mục đích**: Monitor health status của TSID system

### Giải thích chi tiết:

#### 1. Spring Boot Actuator tự động phát hiện:

```java
@Component  // ← Spring tự động scan và register
public class TsidHealthIndicator implements HealthIndicator {
    // Spring Boot Actuator tự động tìm tất cả classes implement HealthIndicator
    // và thêm chúng vào health endpoint
}
```

**Flow:**
```
1. Spring Boot startup
2. Spring scan tất cả @Component classes
3. Tìm thấy TsidHealthIndicator implements HealthIndicator
4. Actuator tự động register vào health endpoint
5. Khi có request GET /actuator/health → Actuator gọi health()
```

#### 2. Khi nào được gọi?

**Tự động khi:**
- ✅ Request đến `/actuator/health`
- ✅ Kubernetes liveness/readiness probes
- ✅ Monitoring tools (Prometheus, Datadog, etc.)
- ✅ Load balancer health checks

**Ví dụ request:**
```bash
# Browser hoặc curl
curl http://localhost:8080/actuator/health

# Response:
{
  "status": "UP",
  "components": {
    "tsid": {                    // ← TsidHealthIndicator
      "status": "UP",
      "details": {
        "status": "TSID system operational",
        "nodeId": 0,
        "testTsid": 1234567890123456789,
        "timestamp": "2024-01-15T12:30:45Z",
        "maxNodeId": 1023,
        "nodeCapacity": "1024 instances"
      }
    },
    "db": { ... },              // Database health
    "redis": { ... },           // Redis health
    "diskSpace": { ... }        // Disk space health
  }
}
```

#### 3. Kubernetes Integration:

```yaml
# Kubernetes deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: silre-backend
spec:
  template:
    spec:
      containers:
      - name: backend
        image: silre-backend:latest
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness  # ← Gọi health endpoint
            port: 8080
          initialDelaySeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness  # ← Gọi health endpoint
            port: 8080
          initialDelaySeconds: 10
```

**Khi Kubernetes gọi:**
```
1. Kubernetes → GET /actuator/health/readiness
2. Spring Actuator → Collects all HealthIndicators
3. Actuator → Calls TsidHealthIndicator.health()
4. TsidHealthIndicator → Checks TSID system
5. Returns → Health status (UP/DOWN)
6. Kubernetes → Decides if pod is ready
```

#### 4. Monitoring Tools Integration:

**Prometheus:**
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'silre-backend'
    metrics_path: '/actuator/health'
    static_configs:
      - targets: ['localhost:8080']
```

**Datadog:**
```yaml
# datadog.yaml
logs:
  - type: http
    path: /actuator/health
```

#### 5. Health Check Flow:

```
┌─────────────────────────────────────────────────────────────┐
│  Request: GET /actuator/health                              │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Spring Boot Actuator                                       │
│  - Collects all HealthIndicator beans                       │
│  - Calls each health() method                               │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  TsidHealthIndicator.health()                               │
│  1. Check: tsidFactory != null?                            │
│  2. Test: Generate a TSID                                  │
│  3. Check: Clock synchronization                            │
│  4. Check: Node ID allocated?                              │
│  5. Return: Health.up() or Health.down()                    │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Response JSON                                              │
│  {                                                          │
│    "status": "UP",                                         │
│    "components": {                                         │
│      "tsid": { ... }                                       │
│    }                                                        │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
```

#### 6. Tại sao cần Health Indicator?

**Benefits:**
- ✅ **Monitoring**: Biết TSID system có hoạt động không
- ✅ **Debugging**: Xem Node ID, test TSID generation
- ✅ **Alerting**: Có thể alert nếu TSID system DOWN
- ✅ **Kubernetes**: Pod readiness/liveness checks
- ✅ **Operations**: DevOps có thể check health nhanh

**Ví dụ use case:**
```bash
# DevOps check health
curl http://production:8080/actuator/health | jq '.components.tsid'

# Output:
{
  "status": "UP",
  "details": {
    "status": "TSID system operational",
    "nodeId": 5,
    "testTsid": 1234567890123456789,
    "warning": "Clock synchronization issue detected. Time difference: 2000 ms."
  }
}

# → DevOps biết:
# - TSID system đang hoạt động
# - Node ID = 5
# - ⚠️ Có warning về clock sync (cần fix NTP)
```

### Kết luận:

- **Tự động**: Spring Boot Actuator tự động phát hiện và sử dụng
- **Khi nào**: Mỗi khi có request đến `/actuator/health`
- **Mục đích**: Monitor, debug, và alert về TSID system health
- **Production**: Rất quan trọng cho operations và monitoring

---

## 3. Có thể bỏ `session` và `object` không?

**Không thể!** Vì:
- Interface `IdentifierGenerator` yêu cầu signature này
- Hibernate sẽ gọi method với 2 tham số này
- Nếu không match signature → Compile error

**Best practice:**
- Giữ signature đúng interface
- Không dùng tham số không cần thiết (như code hiện tại)
- Có thể thêm comment giải thích tại sao không dùng

---

## 4. Có thể dùng `session` hoặc `object` trong tương lai không?

**Có thể!** Nếu cần:

**Ví dụ: Logging entity info:**
```java
@Override
public Serializable generate(SharedSessionContractImplementor session, Object object) {
    // Có thể log entity info để debug
    if (object instanceof User) {
        User user = (User) object;
        logger.debug("Generating TSID for user: {}", user.getEmail());
    }
    
    return tsidFactory.create().toLong();
}
```

**Ví dụ: Check entity state:**
```java
@Override
public Serializable generate(SharedSessionContractImplementor session, Object object) {
    // Có thể check entity state
    if (object instanceof Post && ((Post) object).isDraft()) {
        // Use different ID strategy for drafts
        return generateDraftId();
    }
    
    return tsidFactory.create().toLong();
}
```

**Nhưng hiện tại không cần** → Code hiện tại là đúng và clean! ✅
