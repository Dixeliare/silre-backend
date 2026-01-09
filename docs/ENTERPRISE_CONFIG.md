# Enterprise Configuration Guide

## 📋 Tổng quan

Tài liệu này mô tả cách các doanh nghiệp lớn cấu hình Spring Boot application với Redis và TSID trong môi trường production.

---

## 🏗️ Kiến trúc Enterprise

### 1. Externalized Configuration (Cấu hình ngoài)

**Nguyên tắc:** Không bao giờ hardcode credentials trong code.

#### Cách 1: Environment Variables (Khuyến nghị)

```bash
# Set environment variables
export DB_URL=jdbc:postgresql://prod-db:5432/silre_backend
export DB_USERNAME=app_user
export DB_PASSWORD=secure_password_123
export REDIS_HOST=redis-cluster.production.internal
export REDIS_PORT=6379
export REDIS_PASSWORD=redis_secure_password

# Run application
java -jar app.jar --spring.profiles.active=prod
```

#### Cách 2: Kubernetes ConfigMaps & Secrets

```yaml
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: silre-backend-config
data:
  application-prod.yaml: |
    spring:
      data:
        redis:
          host: redis-cluster.production.internal
          port: "6379"

---
# secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: silre-backend-secrets
type: Opaque
stringData:
  DB_PASSWORD: secure_password_123
  REDIS_PASSWORD: redis_secure_password
```

#### Cách 3: Spring Cloud Config Server

```yaml
# config-server application.yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://git.company.com/config-repo
          search-paths: '{application}/{profile}'
```

---

## 🔄 Environment Profiles

### Development (`application-dev.yaml`)

- **Redis:** Local single instance
- **TSID:** Dev mode enabled (không cần Redis)
- **Logging:** DEBUG level
- **Security:** Relaxed

### Staging (`application-staging.yaml`)

- **Redis:** Staging cluster (mirror production)
- **TSID:** Production mode (Redis required)
- **Logging:** INFO level
- **Security:** Same as production

### Production (`application-prod.yaml`)

- **Redis:** High Availability (Cluster/Sentinel)
- **TSID:** Production mode (fail-fast)
- **Logging:** WARN level, structured JSON
- **Security:** Full encryption, authentication

---

## 🚀 Redis High Availability Patterns

### Pattern 1: Redis Sentinel (Recommended)

```yaml
spring:
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - redis-sentinel-1:26379
          - redis-sentinel-2:26379
          - redis-sentinel-3:26379
      password: ${REDIS_PASSWORD}
```

**Ưu điểm:**
- Automatic failover
- High availability
- Read replicas support

### Pattern 2: Redis Cluster

```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - redis-node-1:6379
          - redis-node-2:6379
          - redis-node-3:6379
        max-redirects: 3
      password: ${REDIS_PASSWORD}
```

**Ưu điểm:**
- Horizontal scaling
- Sharding automatic
- High throughput

### Pattern 3: Managed Redis Service (Cloud)

**AWS ElastiCache:**
```yaml
spring:
  data:
    redis:
      host: ${ELASTICACHE_ENDPOINT}
      port: 6379
      password: ${ELASTICACHE_AUTH_TOKEN}
      ssl:
        enabled: true
```

**Azure Cache for Redis:**
```yaml
spring:
  data:
    redis:
      host: ${AZURE_REDIS_HOST}
      port: 6380  # SSL port
      password: ${AZURE_REDIS_KEY}
      ssl:
        enabled: true
```

---

## 📊 Monitoring & Observability

### 1. Health Checks

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true  # Kubernetes liveness/readiness probes
```

**Kubernetes Integration:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
```

### 2. Metrics (Prometheus)

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
```

### 3. Distributed Tracing (Optional)

```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
```

---

## 🔒 Security Best Practices

### 1. Credentials Management

**❌ NEVER:**
```yaml
password: mypassword123  # Hardcoded
```

**✅ ALWAYS:**
```yaml
password: ${REDIS_PASSWORD}  # Environment variable
```

### 2. SSL/TLS Encryption

```yaml
spring:
  data:
    redis:
      ssl:
        enabled: true
        trust-store: ${REDIS_TRUST_STORE}
        trust-store-password: ${REDIS_TRUST_STORE_PASSWORD}
```

### 3. Network Security

- **Firewall Rules:** Chỉ cho phép app servers kết nối Redis
- **VPC/Private Network:** Redis không expose ra internet
- **Authentication:** Luôn dùng password

---

## 🐳 Docker & Kubernetes Deployment

### Docker Compose (Development)

```yaml
version: '3.8'
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --requirepass ${REDIS_PASSWORD}
  
  backend:
    image: silre-backend:latest
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - REDIS_HOST=redis
      - REDIS_PORT=6379
    depends_on:
      - redis
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: silre-backend
spec:
  replicas: 3  # Multiple instances
  template:
    spec:
      containers:
      - name: backend
        image: silre-backend:latest
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: REDIS_HOST
          valueFrom:
            configMapKeyRef:
              name: redis-config
              key: host
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: redis-secrets
              key: password
```

---

## 📈 Connection Pooling (Production Tuning)

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 32      # Max connections
          max-idle: 16        # Max idle connections
          min-idle: 8         # Min idle (keep warm)
          max-wait: 2000ms    # Max wait time
        shutdown-timeout: 100ms
```

**Tuning Guidelines:**
- `max-active` = 2x số concurrent requests
- `min-idle` = giữ connections warm để giảm latency
- Monitor pool metrics qua Actuator

---

## ✅ Checklist Production

- [ ] Redis Cluster/Sentinel configured
- [ ] All credentials từ environment variables
- [ ] SSL/TLS enabled
- [ ] Connection pooling tuned
- [ ] Health checks configured
- [ ] Metrics exported (Prometheus)
- [ ] Logging structured (JSON)
- [ ] Monitoring alerts setup
- [ ] Backup strategy for Redis
- [ ] Disaster recovery plan

---

## 🔗 References

- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Redis High Availability](https://redis.io/docs/management/sentinel/)
- [Spring Data Redis](https://docs.spring.io/spring-data/redis/docs/current/reference/html/)
