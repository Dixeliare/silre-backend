# TSID Production Deployment - Auto-Restart & Self-Healing

## 🎯 Vấn đề

**Fail-Fast là intentional** để prevent data corruption, nhưng trong production cần có **auto-restart mechanism** để đảm bảo high availability.

---

## ✅ Giải pháp: Auto-Restart + Self-Healing

### 1. **Fail-Fast → Auto-Restart → Self-Healing**

```
Timeline:
00:00 - Instance A: Node ID 0, Redis key exists
04:30 - Redis restart → Key bị mất
04:31 - Instance A: refreshTsidLock() detects lock lost
        → FAIL-FAST: System.exit(1)
        → App crashes
04:32 - Auto-restart mechanism (systemd/Docker/K8s) detects crash
        → Restart instance A
04:33 - Instance A: allocateNodeId() runs
        → Tries Node ID 0 → SETNX → Success (key was lost)
        → Re-acquires Node ID 0
        → ✅ Self-healing complete!
```

**Kết quả:** Instance tự động restart và re-acquire Node ID → **Zero downtime** (nếu có multiple instances)

---

## 🚀 Production Deployment Options

### Option 1: **Docker with Restart Policy** (Recommended for Docker)

```yaml
# docker-compose.yml
version: '3.8'
services:
  backend:
    image: silre-backend:latest
    restart: always  # Auto-restart on crash
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      - redis
      - postgres
  
  redis:
    image: redis:7-alpine
    restart: always
    volumes:
      - redis-data:/data
  
  postgres:
    image: postgres:15
    restart: always
    volumes:
      - postgres-data:/var/lib/postgresql/data
```

**Docker restart policies:**
- `always`: Restart always (even on manual stop)
- `unless-stopped`: Restart unless manually stopped
- `on-failure`: Restart only on failure (exit code != 0)

**Command:**
```bash
docker run -d --restart=always silre-backend:latest
```

---

### Option 2: **systemd Service** (Recommended for VPS/Bare Metal)

```ini
# /etc/systemd/system/silre-backend.service
[Unit]
Description=Silre Backend Application
After=network.target postgresql.service redis.service

[Service]
Type=simple
User=appuser
WorkingDirectory=/opt/silre-backend
ExecStart=/usr/bin/java -jar /opt/silre-backend/silre-backend.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

# Auto-restart on failure
Restart=on-failure
RestartSec=10

# Resource limits
LimitNOFILE=65536
MemoryLimit=2G

[Install]
WantedBy=multi-user.target
```

**systemd restart policies:**
- `always`: Restart always
- `on-failure`: Restart only on failure (exit code != 0) ✅ **Recommended**
- `on-abnormal`: Restart on abnormal termination
- `on-watchdog`: Restart on watchdog timeout

**Commands:**
```bash
# Enable and start service
sudo systemctl enable silre-backend
sudo systemctl start silre-backend

# Check status
sudo systemctl status silre-backend

# View logs
sudo journalctl -u silre-backend -f
```

---

### Option 3: **Kubernetes Deployment** (Recommended for K8s)

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: silre-backend
spec:
  replicas: 3  # Multiple instances for high availability
  selector:
    matchLabels:
      app: silre-backend
  template:
    metadata:
      labels:
        app: silre-backend
    spec:
      containers:
      - name: backend
        image: silre-backend:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        # Auto-restart on crash
        restartPolicy: Always
        
        # Health checks
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 5
          failureThreshold: 3
        
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
        
        # Resource limits
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
```

**Kubernetes automatically:**
- ✅ Restarts crashed containers
- ✅ Maintains desired replica count
- ✅ Distributes traffic across healthy instances
- ✅ Handles rolling updates

---

## 🔄 Self-Healing Flow

### Scenario: Redis Restart → Lock Lost → Auto-Restart

```
┌─────────────────────────────────────────────────────────────┐
│ Step 1: Instance Running                                    │
│ Instance A: Node ID 0, Redis key = "instance:A:pid:time"   │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ Step 2: Redis Restart                                        │
│ Redis: All keys lost (no persistence)                        │
│ Instance A: Still running with Node ID 0 in memory          │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ Step 3: Refresh Detects Lock Lost (within 12 hours)         │
│ refreshTsidLock() → currentValue == null                     │
│ → System.exit(1) → FAIL-FAST                                │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ Step 4: Auto-Restart Mechanism                              │
│ systemd/Docker/K8s detects exit code != 0                  │
│ → Restart instance A (within 10 seconds)                   │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ Step 5: Self-Healing                                        │
│ Instance A: allocateNodeId() runs                          │
│ → Tries Node ID 0 → SETNX → Success (key was lost)        │
│ → Re-acquires Node ID 0                                     │
│ → ✅ Back to normal operation!                              │
└─────────────────────────────────────────────────────────────┘
```

**Total downtime:** ~10-30 seconds (restart time)

---

## 📊 Production Best Practices

### 1. **Multiple Instances** (High Availability)

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Instance A  │     │ Instance B  │     │ Instance C  │
│ Node ID: 0  │     │ Node ID: 1  │     │ Node ID: 2  │
└─────────────┘     └─────────────┘     └─────────────┘
       │                   │                   │
       └───────────────────┴───────────────────┘
                          │
                   ┌──────────────┐
                   │ Load Balancer│
                   └──────────────┘
```

**Benefits:**
- ✅ Zero downtime during restart (other instances handle traffic)
- ✅ Load distribution
- ✅ Fault tolerance

---

### 2. **Monitoring & Alerting**

```yaml
# Prometheus alert rules
groups:
- name: tsid_lock_alerts
  rules:
  - alert: TSIDLockLost
    expr: increase(tsid_lock_lost_total[5m]) > 0
    annotations:
      summary: "TSID lock lost - instance restarted"
      description: "Instance {{ $labels.instance }} lost TSID lock and restarted"
  
  - alert: TSIDLockStolen
    expr: increase(tsid_lock_stolen_total[5m]) > 0
    annotations:
      summary: "TSID lock stolen - potential ID collision prevented"
      description: "Instance {{ $labels.instance }} detected lock theft"
```

**Monitoring metrics:**
- `tsid_lock_lost_total`: Counter of lock loss events
- `tsid_lock_stolen_total`: Counter of lock theft events
- `tsid_node_id`: Current Node ID (gauge)
- `tsid_lock_ttl_seconds`: Remaining TTL (gauge)

---

### 3. **Redis Persistence** (Optional but Recommended)

```conf
# redis.conf
# Enable AOF (Append Only File) persistence
appendonly yes
appendfsync everysec

# Enable RDB snapshots
save 900 1
save 300 10
save 60 10000
```

**Benefits:**
- ✅ Redis keys survive restarts
- ✅ Reduces lock loss events
- ⚠️ Trade-off: Slight performance impact

**Note:** Even with persistence, fail-fast is still needed (Redis can still crash/lose data)

---

### 4. **Graceful Shutdown** (Future Enhancement)

```java
// Instead of System.exit(1), use graceful shutdown
@Scheduled(fixedDelayString = "PT12H")
public void refreshTsidLock() {
    String currentValue = redisTemplate.opsForValue().get(key);
    
    if (currentValue == null || !currentValue.equals(instanceId)) {
        logger.error("CRITICAL: TSID lock lost or stolen - initiating graceful shutdown");
        
        // Graceful shutdown (allows in-flight requests to complete)
        applicationContext.close();
        return;
    }
    
    // Refresh TTL
    redisTemplate.expire(key, Duration.ofHours(24));
}
```

**Benefits:**
- ✅ Clean shutdown (no abrupt termination)
- ✅ In-flight requests complete
- ✅ Better for production

---

## 🧪 Testing Auto-Restart

### Test 1: Docker Restart Policy

```bash
# 1. Start container with restart policy
docker run -d --name backend --restart=always silre-backend:latest

# 2. Simulate lock loss (delete Redis key)
redis-cli DEL "sys:tsid:node:0"

# 3. Wait for refresh (or trigger manually)
# Expected: Container restarts automatically

# 4. Check container status
docker ps -a | grep backend
# Expected: Container restarted, new container ID

# 5. Check logs
docker logs backend | tail -20
# Expected: "Re-acquired Node ID 0"
```

---

### Test 2: systemd Auto-Restart

```bash
# 1. Start service
sudo systemctl start silre-backend

# 2. Simulate lock loss
redis-cli DEL "sys:tsid:node:0"

# 3. Wait for refresh
# Expected: Service restarts automatically

# 4. Check service status
sudo systemctl status silre-backend
# Expected: "Active: active (running)" (after restart)

# 5. Check logs
sudo journalctl -u silre-backend -n 50
# Expected: "Re-acquired Node ID 0"
```

---

### Test 3: Kubernetes Auto-Restart

```bash
# 1. Deploy application
kubectl apply -f k8s/deployment.yaml

# 2. Simulate lock loss
kubectl exec -it redis-pod -- redis-cli DEL "sys:tsid:node:0"

# 3. Wait for refresh
# Expected: Pod restarts automatically

# 4. Check pod status
kubectl get pods -l app=silre-backend
# Expected: Pod restarted, new pod name

# 5. Check logs
kubectl logs -l app=silre-backend --tail=50
# Expected: "Re-acquired Node ID 0"
```

---

## 📝 Summary

### ✅ **Production Setup Checklist:**

1. ✅ **Auto-restart mechanism** (systemd/Docker/K8s)
2. ✅ **Multiple instances** (high availability)
3. ✅ **Monitoring & alerting** (detect lock loss events)
4. ✅ **Redis persistence** (optional, reduces lock loss)
5. ✅ **Health checks** (liveness/readiness probes)

### ✅ **Self-Healing Flow:**

1. ✅ Lock lost → Fail-fast (crash)
2. ✅ Auto-restart mechanism detects crash
3. ✅ Instance restarts
4. ✅ Re-acquires Node ID (same or different)
5. ✅ Back to normal operation

### ⚠️ **Important Notes:**

1. ⚠️ **Fail-fast is intentional** - prevents data corruption
2. ⚠️ **Auto-restart is required** - ensures high availability
3. ⚠️ **Multiple instances recommended** - zero downtime during restart
4. ⚠️ **Monitoring is essential** - detect and alert on lock loss events

---

## 🎓 Best Practices

1. ✅ **Fail-Fast + Auto-Restart** = Safe + Available
2. ✅ **Multiple Instances** = Zero Downtime
3. ✅ **Monitoring** = Visibility into lock health
4. ✅ **Redis Persistence** = Reduces lock loss events
5. ✅ **Health Checks** = Fast failure detection

---

## ✅ Kết luận

**Trong production:**
- ✅ **Fail-fast** để prevent corruption (intentional crash)
- ✅ **Auto-restart** để ensure availability (systemd/Docker/K8s)
- ✅ **Self-healing** khi restart (re-acquire Node ID)
- ✅ **Multiple instances** để zero downtime

**Không cần restart thủ công** - auto-restart mechanism sẽ handle!
