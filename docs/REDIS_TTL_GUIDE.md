# Redis TTL Command - Hướng dẫn kiểm tra TTL

## 📋 Redis TTL Return Values

Khi dùng lệnh `TTL key` trong Redis, bạn sẽ nhận được các giá trị sau:

| Giá trị | Ý nghĩa | Giải thích |
|---------|---------|------------|
| `-2` | Key không tồn tại | Key chưa được tạo hoặc đã bị xóa |
| `-1` | Key không có expiration | Key tồn tại nhưng không có TTL (permanent) |
| `>= 0` | Số giây còn lại | Key còn sống trong X giây nữa |

---

## 🔍 Cách kiểm tra TTL của TSID Node Keys

### 1. Kiểm tra TTL của một key cụ thể:

```bash
# Kiểm tra TTL (trả về số giây)
redis-cli TTL "sys:tsid:node:0"

# Output examples:
# (integer) 86400  → Còn 86400 giây = 24 giờ
# (integer) 43200  → Còn 43200 giây = 12 giờ
# (integer) 3600   → Còn 3600 giây = 1 giờ
# (integer) -1     → Key không có expiration (không nên xảy ra)
# (integer) -2     → Key không tồn tại
```

### 2. Kiểm tra TTL của tất cả node keys:

```bash
# List tất cả keys và TTL của chúng
redis-cli --scan --pattern "sys:tsid:node:*" | while read key; do
    ttl=$(redis-cli TTL "$key")
    if [ "$ttl" -ge 0 ]; then
        hours=$((ttl / 3600))
        minutes=$(((ttl % 3600) / 60))
        echo "$key: $ttl seconds ($hours hours $minutes minutes)"
    elif [ "$ttl" -eq -1 ]; then
        echo "$key: No expiration (permanent)"
    else
        echo "$key: Key does not exist"
    fi
done
```

### 3. Kiểm tra TTL và phân loại:

```bash
# Script để check và phân loại keys
redis-cli --scan --pattern "sys:tsid:node:*" | while read key; do
    ttl=$(redis-cli TTL "$key")
    if [ "$ttl" -ge 0 ]; then
        hours=$((ttl / 3600))
        if [ "$hours" -gt 20 ]; then
            echo "⚠️  STALE: $key - TTL: ${hours}h (will be cleaned up)"
        else
            echo "✅ ACTIVE: $key - TTL: ${hours}h"
        fi
    fi
done
```

### 4. Kiểm tra TTL với format dễ đọc:

```bash
# One-liner để check TTL của tất cả node keys
redis-cli --scan --pattern "sys:tsid:node:*" | \
  xargs -I {} sh -c 'ttl=$(redis-cli TTL "{}"); \
  if [ "$ttl" -ge 0 ]; then \
    hours=$((ttl / 3600)); \
    mins=$(((ttl % 3600) / 60)); \
    echo "{}: ${hours}h ${mins}m (${ttl}s)"; \
  elif [ "$ttl" -eq -1 ]; then \
    echo "{}: PERMANENT (no expiration)"; \
  else \
    echo "{}: NOT EXISTS"; \
  fi'
```

---

## 🛠️ Useful Redis Commands cho TSID Keys

### 1. Xem tất cả node keys:

```bash
redis-cli --scan --pattern "sys:tsid:node:*"
```

### 2. Đếm số lượng node keys:

```bash
redis-cli --scan --pattern "sys:tsid:node:*" | wc -l
```

### 3. Xem TTL của một key cụ thể:

```bash
redis-cli TTL "sys:tsid:node:0"
```

### 4. Xem value của key:

```bash
redis-cli GET "sys:tsid:node:0"
# Output: "LOCKED"
```

### 5. Xem thông tin chi tiết của key:

```bash
# TTL + Value
redis-cli --raw TTL "sys:tsid:node:0" && redis-cli GET "sys:tsid:node:0"
```

### 6. Xóa một key cụ thể:

```bash
redis-cli DEL "sys:tsid:node:0"
```

### 7. Xóa tất cả node keys:

```bash
redis-cli --scan --pattern "sys:tsid:node:*" | xargs redis-cli DEL
```

### 8. Xem keys với TTL > 20 giờ (stale keys):

```bash
redis-cli --scan --pattern "sys:tsid:node:*" | while read key; do
    ttl=$(redis-cli TTL "$key")
    if [ "$ttl" -gt 72000 ]; then  # 20 hours = 72000 seconds
        hours=$((ttl / 3600))
        echo "STALE: $key - TTL: ${hours}h"
    fi
done
```

---

## 📊 Example Output

### Scenario 1: Active key (recently refreshed)

```bash
$ redis-cli TTL "sys:tsid:node:0"
(integer) 43200

# → Còn 43200 giây = 12 giờ
# → Key này vừa được refresh (active instance)
```

### Scenario 2: Stale key (from previous session)

```bash
$ redis-cli TTL "sys:tsid:node:1"
(integer) 79200

# → Còn 79200 giây = 22 giờ
# → Key này là stale (TTL > 20h)
# → Sẽ bị cleanup bởi scheduled task hoặc khi allocate
```

### Scenario 3: Key không tồn tại

```bash
$ redis-cli TTL "sys:tsid:node:999"
(integer) -2

# → Key không tồn tại
# → Slot này free, có thể allocate
```

### Scenario 4: Key không có expiration (shouldn't happen)

```bash
$ redis-cli TTL "sys:tsid:node:2"
(integer) -1

# → Key tồn tại nhưng không có expiration
# → Điều này không nên xảy ra với TSID keys
# → Nếu xảy ra, cần manual cleanup
```

---

## 🔧 Troubleshooting

### Problem: Key có TTL = -1 (permanent)

**Nguyên nhân:** Key được tạo không có expiration

**Giải pháp:**
```bash
# Xóa key và để backend allocate lại
redis-cli DEL "sys:tsid:node:0"

# Hoặc set TTL manually
redis-cli EXPIRE "sys:tsid:node:0" 86400  # 24 hours
```

### Problem: Nhiều stale keys tích lũy

**Nguyên nhân:** Scheduled cleanup chưa chạy hoặc Redis restart

**Giải pháp:**
```bash
# Manual cleanup tất cả stale keys
redis-cli --scan --pattern "sys:tsid:node:*" | while read key; do
    ttl=$(redis-cli TTL "$key")
    if [ "$ttl" -gt 72000 ]; then  # > 20 hours
        redis-cli DEL "$key"
        echo "Deleted: $key"
    fi
done
```

### Problem: Không biết key nào đang active

**Giải pháp:**
```bash
# List tất cả keys với status
redis-cli --scan --pattern "sys:tsid:node:*" | while read key; do
    ttl=$(redis-cli TTL "$key")
    if [ "$ttl" -ge 0 ]; then
        hours=$((ttl / 3600))
        if [ "$hours" -gt 20 ]; then
            status="STALE"
        else
            status="ACTIVE"
        fi
        echo "$status: $key - TTL: ${hours}h"
    fi
done
```

---

## 📝 Summary

| Command | Purpose | Output |
|---------|---------|--------|
| `TTL key` | Check TTL của key | `-2` (not exists), `-1` (no expiration), `>= 0` (seconds) |
| `GET key` | Xem value của key | `"LOCKED"` |
| `DEL key` | Xóa key | `1` (deleted) or `0` (not found) |
| `EXPIRE key seconds` | Set TTL cho key | `1` (set) or `0` (key not found) |

**Key Points:**
- ✅ `TTL >= 0`: Key còn sống, số giây còn lại
- ⚠️ `TTL = -1`: Key không có expiration (cần manual cleanup)
- ❌ `TTL = -2`: Key không tồn tại (free slot)
