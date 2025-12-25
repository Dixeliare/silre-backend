# PROJECT THREADIT - TECHNICAL DESIGN DOCUMENT

**Project Name:** ThreadIt (Social Forum Platform)  
**Version:** 1.0.0  
**Author:** LongDx  
**Last Updated:** December 2025

## 1. TỔNG QUAN (EXECUTIVE SUMMARY)

ThreadIt là một nền tảng mạng xã hội thảo luận (Discussion Platform) hiệu năng cao, kết hợp giữa cấu trúc thảo luận chiều sâu của Reddit và trải nghiệm Newsfeed thời gian thực của Threads/Twitter.

Hệ thống được thiết kế theo tiêu chuẩn Enterprise/Fintech, tập trung vào tính toàn vẹn dữ liệu (Data Consistency), khả năng mở rộng (Scalability) và bảo mật (Security).

### Mục tiêu kỹ thuật (Technical Goals)
*   **High Performance:** API response time < 50ms cho 95% request.
*   **Global Accessibility:** Hỗ trợ định danh và tìm kiếm người dùng đa ngôn ngữ (bao gồm cả CJK - Trung/Nhật/Hàn).
*   **Scalability:** Kiến trúc sẵn sàng mở rộng lên 1M+ users (Database Sharding & Caching ready).

---

## 2. KIẾN TRÚC HỆ THỐNG (SYSTEM ARCHITECTURE)

Hệ thống sử dụng mô hình Monolithic Architecture (được module hóa chặt chẽ), sẵn sàng tách thành Microservices khi cần thiết.

### 2.1. Tech Stack (Công nghệ lõi)

| Hạng mục | Công nghệ | Phiên bản | Lý do lựa chọn |
| :--- | :--- | :--- | :--- |
| Backend | Java Spring Boot | 3.4.x / Java 21 LTS | Ổn định, hỗ trợ Virtual Threads (Project Loom) cho High Concurrency. |
| Database | PostgreSQL | 16 | Xử lý quan hệ phức tạp, hỗ trợ JSONB và Recursive Query tốt nhất. |
| Caching | Redis | 7 | Lưu trữ Hot Data, Session và tính toán Feed Ranking (ZSET). |
| Migration | Flyway | Latest | Quản lý Version Database an toàn (Code First + Migration). |
| Utility | NanoID + ICU4J | 2.0 / 74.2 | Hybrid: NanoID (Bảo mật) + ICU4J (Latin hóa tên hiển thị). |
| Deployment | Docker | Latest | Đóng gói và triển khai đồng nhất. |

> [!NOTE]
> Để biết chi tiết về hệ thống quản trị (CMS) và giải pháp giám sát (Monitoring/Observability), vui lòng xem tại [admin-monitoring-spec.md](file:///Users/techmax/Documents/GitHub/forum-backend/docs/admin-monitoring-spec.md).

### 2.2. High-Level Design Diagram

```mermaid
graph TD
    Client[Client (Next.js/Mobile)] -->|HTTPS| LB[Load Balancer / Nginx]
    LB -->|REST API| API[Spring Boot Application]
    
    subgraph "Application Layer"
        API -->|Auth/Security| Security[Spring Security + JWT]
        API -->|Data Access| Repo[JPA Repositories]
        API -->|Ranking Logic| Service[Feed Service]
    end

    subgraph "Data Layer"
        Repo -->|Read/Write| DB[(PostgreSQL)]
        Service -->|Cache/Score| Redis[(Redis)]
    end
    
    subgraph "External Services"
        Client -->|Upload Media| MediaHost[External Host (jpg.fish)]
    end
```

---

## 3. THIẾT KẾ CƠ SỞ DỮ LIỆU (DATABASE DESIGN)

### 3.1. Chiến lược ID (ID Strategy)

Sử dụng chiến lược Dual-Key Identification (Định danh kép) để tối ưu hóa cả Hiệu năng máy và Trải nghiệm người dùng.

> [!TIP]
> Chi tiết triển khai hệ thống định danh thông minh (Hybrid: Latinized Prefix + NanoID Suffix) có thể xem tại [user-identity-spec.md](file:///Users/techmax/Documents/GitHub/forum-backend/docs/user-identity-spec.md).

**Internal ID (Dùng cho Máy):**
*   **Công nghệ:** TSID (Time-Sorted Unique Identifier).
*   **Kiểu dữ liệu:** BIGINT (64-bit).
*   **Lợi ích:** Tương thích hoàn hảo với B-Tree Index của PostgreSQL, không gây phân mảnh trang (Page Splitting) như UUID, sắp xếp được theo thời gian.

**Public ID (Dùng cho Người):**
*   **User Tag:** `LatinizedName` + `#` + `NanoID`. (Ví dụ: `LL#Xy9z` cho user "李小龙").
*   **Cấu trúc:** Prefix (xử lý bởi ICU4J) giúp dễ đọc + Suffix (NanoID) đảm bảo duy nhất và bảo mật.
*   **Lợi ích:** URL thân thiện, hỗ trợ tìm kiếm toàn cầu, đồng thời bảo vệ quyền riêng tư (Suffix ngẫu nhiên).

### 3.2. Schema Chính (Key Entities)

*   **Users (users):**
    *   id (PK, TSID): Khóa chính.
    *   username (Unique): Tên đăng nhập hệ thống.
    *   display_name: Tên hiển thị (UTF-8, trùng nhau thoải mái).
    *   email: Unique.
*   **Posts (posts):**
    *   id (PK, TSID).
    *   content: Chứa Markdown.
    *   media_url: Chỉ lưu Link ảnh/video (không lưu file binary).
    *   score: Điểm xếp hạng (Ranking Score).
*   **Comments (comments):**
    *   Sử dụng mô hình Adjacency List (parent_id) kết hợp với Recursive CTE của PostgreSQL để truy vấn cây bình luận đa cấp.

---

## 4. TÍNH NĂNG KỸ THUẬT LÕI (CORE ENGINEERING FEATURES)

### 4.1. Hybrid User Tagging (Smart Global Search)

Kết hợp giữa khả năng tìm kiếm thông minh và bảo mật riêng tư.

*   **Input:** User đặt tên "甘米らくれ".
*   **Processing:**
    1.  **Prefix:** Dùng IBM ICU4J -> Latin hóa -> "GR" (Gợi nhớ).
    2.  **Suffix:** Sinh NanoID ngẫu nhiên -> "7x9A" (Bảo mật).
*   **Output (User Tag):** `GR#7x9A`.
*   **Kết quả:**
    *   User quốc tế dễ dàng gọi tên (@GR...).
    *   Hệ thống bảo mật vì Suffix là ngẫu nhiên, không lộ ID thật.

### 4.2. Gravity Feed Algorithm (Thuật toán xếp hạng)

Sử dụng công thức Gravity Decay (tương tự HackerNews) để tạo Newsfeed "Trending".

$$Score = \frac{(Votes - 1)}{(Time_{hours} + 2)^{1.8}}$$

> [!IMPORTANT]
> Phiên bản nâng cấp **Heart-Based Ranking** (Thả tim thay cho Vote) và các tín hiệu tương tác nâng cao được mô tả chi tiết tại [ranking-algorithm-spec.md](file:///Users/techmax/Documents/GitHub/forum-backend/docs/ranking-algorithm-spec.md).

*   **Cơ chế:**
    *   Khi có Vote mới -> Tính lại Score -> Cập nhật vào Redis Sorted Set (ZSET).
    *   Khi User lướt Feed -> Lấy Top ID từ Redis -> Query chi tiết từ PostgreSQL.
*   **Hiệu năng:** Giảm tải 90% việc sort DB cho PostgreSQL.

---

## 5. BẢO MẬT (SECURITY & COMPLIANCE)

*   **Authentication:** Stateless JWT (Access Token + Refresh Token).
*   **Password:** Bcrypt Hashing (Cost factor 10-12).
*   **API Protection:**
    *   **Rate Limiting:** Sử dụng Bucket4j để giới hạn request (tránh Spam/DDoS).
    *   **Input Validation:** @Valid annotations check data đầu vào chặt chẽ.
    *   **CORS:** Cấu hình chặt chẽ chỉ cho phép Domain Frontend truy cập.

---

## 6. QUY TRÌNH PHÁT TRIỂN (DEVELOPMENT WORKFLOW)

Để đảm bảo code clean và dễ bảo trì (theo chuẩn Bank):

1.  **Code First Approach:** Viết Java Entity trước.
2.  **Versioning:** Sử dụng Flyway để tạo file migration (V1__Init.sql, V2__Add_Column.sql). Tuyệt đối không dùng `ddl-auto=update` trên môi trường Production.
3.  **Layered Architecture:** Controller -> Service (Interface) -> ServiceImpl -> Repository.
4.  **DTO Pattern:** Luôn dùng DTO (Request/Response) để giao tiếp qua API. Không bao giờ trả trực tiếp Entity ra ngoài (tránh lộ Internal ID hoặc Password).

---

## 7. ĐỊNH HƯỚNG MỞ RỘNG (SCALABILITY ROADMAP)

Khi hệ thống đạt 1 triệu Users:

*   **Read Replicas:** Tách 1 DB Master (Ghi) và 2 DB Slaves (Đọc) cho PostgreSQL.
*   **Partitioning:** Chia bảng `posts` và `comments` theo tháng (Range Partitioning).
*   **Search Engine:** Tích hợp Elasticsearch nếu nhu cầu Full-text search phức tạp hơn.

---
*Tài liệu này được bảo lưu và phát triển bởi LongDx.*
