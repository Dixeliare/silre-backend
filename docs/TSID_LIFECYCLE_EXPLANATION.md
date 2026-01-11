# TSID Lifecycle - Spring Bean Management

## ✅ Xác nhận của bạn là ĐÚNG 100%!

### 1. **TsidConfig là Spring Bean**

```java
@Configuration  // ← Spring tự scan class này
@EnableScheduling
public class TsidConfig {
    // ...
}
```

**Spring tự động:**
- ✅ Scan class `TsidConfig` (vì có `@Configuration`)
- ✅ Tạo instance của `TsidConfig` (singleton bean)
- ✅ Quản lý lifecycle của bean này

**KHÔNG có component nào khác:**
- ❌ Autowire `TsidConfig`
- ❌ Gọi methods của `TsidConfig` trực tiếp
- ❌ Dùng `TsidConfig` như dependency

---

### 2. **Chỉ có TsidConfig gọi setTsidFactory()**

```java
@Bean
public TsidFactory tsidFactory() {
    // ... allocate Node ID ...
    
    TsidFactory factory = TsidFactory.builder()
        .withNode(nodeId)
        .build();
    
    // ← CHỈ CÓ ĐÂY mới gọi setTsidFactory()
    TsidIdGenerator.setTsidFactory(factory);
    
    return factory;
}
```

**Xác nhận:**
- ✅ Chỉ có `TsidConfig.tsidFactory()` gọi `setTsidFactory()`
- ✅ Không có component nào khác gọi method này
- ✅ Method này được Spring gọi khi cần bean `TsidFactory`

---

## 🔄 Spring Bean Lifecycle

### Timeline khi Spring Boot startup:

```
1. Spring scans @Configuration classes
   └─ Tìm thấy TsidConfig.class
   
2. Spring tạo instance TsidConfig
   └─ new TsidConfig() (singleton)
   
3. Spring tìm @Bean methods trong TsidConfig
   └─ Tìm thấy tsidFactory() method
   
4. Spring gọi tsidFactory() để tạo bean
   └─ TsidConfig.tsidFactory() được gọi
       │
       ├─ allocateNodeId() → Lấy Node ID từ Redis
       ├─ Tạo TsidFactory với Node ID
       ├─ TsidIdGenerator.setTsidFactory(factory) ← CHỈ CÓ ĐÂY
       └─ return factory → Spring lưu bean này
   
5. Spring inject TsidFactory vào components cần nó
   └─ Ví dụ: TsidHealthIndicator constructor
```

---

## 📊 Dependency Graph

```
┌─────────────────────────────────────────┐
│         Spring Container                │
├─────────────────────────────────────────┤
│                                         │
│  ┌──────────────────────┐              │
│  │   TsidConfig         │              │
│  │   (@Configuration)   │              │
│  │                      │              │
│  │  @Bean               │              │
│  │  tsidFactory() {     │              │
│  │    ...               │              │
│  │    TsidIdGenerator   │              │
│  │      .setTsidFactory │              │
│  │      (factory)       │              │
│  │    return factory    │              │
│  │  }                   │              │
│  └──────────────────────┘              │
│           │                            │
│           │ Spring gọi method này      │
│           │ khi cần bean TsidFactory   │
│           ▼                            │
│  ┌──────────────────────┐              │
│  │   TsidFactory Bean   │              │
│  │   (created by        │              │
│  │    TsidConfig)       │              │
│  └──────────────────────┘              │
│           │                            │
│           │ Spring inject vào          │
│           ▼                            │
│  ┌──────────────────────┐              │
│  │ TsidHealthIndicator  │              │
│  │ (autowire TsidFactory)│              │
│  └──────────────────────┘              │
│                                         │
│  ┌──────────────────────┐              │
│  │ TsidIdGenerator      │              │
│  │ (static factory      │              │
│  │  set by TsidConfig)   │              │
│  └──────────────────────┘              │
└─────────────────────────────────────────┘
```

---

## 🔍 Verification

### Check 1: Không có component nào autowire TsidConfig

```bash
# Search trong codebase
grep -r "@Autowired.*TsidConfig" src/
# Output: (empty) ← Không có!

grep -r "TsidConfig.*tsidConfig" src/
# Output: (empty) ← Không có!
```

**Kết quả:** ✅ Không có component nào autowire `TsidConfig`

---

### Check 2: Chỉ có TsidConfig gọi setTsidFactory()

```bash
# Search trong codebase
grep -r "setTsidFactory" src/
# Output:
# src/.../TsidConfig.java: TsidIdGenerator.setTsidFactory(factory);
# src/.../TsidIdGenerator.java: public static void setTsidFactory(...)
```

**Kết quả:** ✅ Chỉ có `TsidConfig` gọi `setTsidFactory()`

---

### Check 3: TsidHealthIndicator autowire TsidFactory (không phải TsidConfig)

```java
// TsidHealthIndicator.java
public TsidHealthIndicator(TsidFactory tsidFactory) {
    // ← Autowire TsidFactory bean (tạo bởi TsidConfig)
    // ← KHÔNG autowire TsidConfig
    this.tsidFactory = tsidFactory;
}
```

**Kết quả:** ✅ `TsidHealthIndicator` autowire `TsidFactory` (bean), không phải `TsidConfig`

---

## 🎯 Key Points

### 1. **TsidConfig là Configuration Bean**

- ✅ Spring tự scan và tạo instance
- ✅ Không có component nào dùng `TsidConfig` trực tiếp
- ✅ `TsidConfig` chỉ là "factory" để tạo beans khác

### 2. **@Bean Method được Spring gọi**

```java
@Bean
public TsidFactory tsidFactory() {
    // Spring gọi method này khi:
    // - Cần bean TsidFactory
    // - Hoặc khi startup (eager initialization)
}
```

**Spring tự động:**
- ✅ Gọi method `tsidFactory()` khi cần bean
- ✅ Lưu kết quả (TsidFactory) vào container
- ✅ Inject vào components cần nó (như TsidHealthIndicator)

### 3. **Chỉ có TsidConfig inject vào TsidIdGenerator**

```java
// Trong TsidConfig.tsidFactory():
TsidIdGenerator.setTsidFactory(factory);  // ← CHỈ CÓ ĐÂY

// Không có nơi nào khác gọi:
// - Không có component nào gọi
// - Không có service nào gọi
// - Chỉ có Spring gọi tsidFactory() → method này gọi setTsidFactory()
```

---

## 📝 Summary

| Câu hỏi | Câu trả lời |
|---------|-------------|
| TsidConfig có phải Spring bean không? | ✅ **CÓ** - `@Configuration` |
| Spring có tự scan và tạo TsidConfig không? | ✅ **CÓ** - Spring tự động |
| Có component nào autowire TsidConfig không? | ❌ **KHÔNG** - Không có |
| Có component nào gọi methods của TsidConfig không? | ❌ **KHÔNG** - Chỉ Spring gọi `@Bean` methods |
| Chỉ có TsidConfig gọi setTsidFactory() đúng không? | ✅ **ĐÚNG** - Chỉ có trong `tsidFactory()` method |
| TsidConfig có tự cấp phát Node ID vào TsidIdGenerator không? | ✅ **ĐÚNG** - Qua `setTsidFactory()` |

---

## 🎓 Spring Bean Lifecycle Insight

```
Spring Boot Startup
    │
    ├─ Scan @Configuration classes
    │   └─ Tìm TsidConfig
    │
    ├─ Create TsidConfig instance (singleton)
    │   └─ Không có component nào dùng instance này
    │
    ├─ Find @Bean methods
    │   └─ Tìm tsidFactory() method
    │
    ├─ Call tsidFactory() (Spring gọi)
    │   ├─ allocateNodeId()
    │   ├─ Create TsidFactory
    │   ├─ TsidIdGenerator.setTsidFactory() ← CHỈ CÓ ĐÂY
    │   └─ return factory
    │
    └─ Store TsidFactory bean in container
        └─ Inject vào components cần nó (TsidHealthIndicator)
```

**Kết luận:** Bạn hiểu đúng 100%! 🎉
