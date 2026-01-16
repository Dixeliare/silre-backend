# TECHNICAL DESIGN DOCUMENT (TDD)

**Project:** Silre - Social Platform  
**Version:** 2.0 (Community-First Architecture)  
**Status:** Approved  
**Author:** LongDx  
**Last Updated:** January 2026

## 1. TỔNG QUAN HỆ THỐNG (SYSTEM OVERVIEW)

### 1.1. Mục tiêu (Goal)
Xây dựng Backend cho mạng xã hội hiện đại (Social Media), tập trung vào các cộng đồng (Communities) được phân loại linh hoạt bằng hệ thống Tags (giống Twitter/Manga).
Hệ thống phải đảm bảo tính mở rộng (Scalability), chịu tải cao (High Concurrency) và trải nghiệm người dùng mượt mà.

**Triết lý thiết kế:**
- **Community là "Nguồn cấp":** User join community để nội dung tự động xuất hiện trong Feed, không phải phải vào "phòng" để xem.
- **Loại bỏ Forum:** Không có cấu trúc Forum/Thread truyền thống để tránh làm rối UI và UX.

### 1.2. Phạm vi (Scope)
*   User Identity (Dual-Key: Internal TSID & Public NanoID)
*   Community Management (Tag-Based Classification)
*   Content Delivery (Adaptive Feed Algorithm)
*   Interaction (Smart Tagging & Notifications)
*   **Global Accessibility:** Hỗ trợ định danh và tìm kiếm người dùng đa ngôn ngữ (bao gồm cả CJK - Trung/Nhật/Hàn).
*   **Scalability:** Kiến trúc sẵn sàng mở rộng lên 1M+ users (Database Sharding & Caching ready).

---

## 2. KIẾN TRÚC HỆ THỐNG (SYSTEM ARCHITECTURE)

Hệ thống sử dụng mô hình **Modular Monolith Architecture** (được module hóa chặt chẽ), sẵn sàng tách thành Microservices khi cần thiết.

### 2.1. Tech Stack (Công nghệ lõi)

| Hạng mục | Công nghệ | Phiên bản | Lý do lựa chọn |
| :--- | :--- | :--- | :--- |
| **Backend Core** | **Java Spring Boot** | 4.0.x | Modular Monolith, High Concurrency. |
| **Worker (AI/Algo)** | **Python** | 3.11+ | Xử lý thuật toán "Gravity" và các tác vụ Data nặng. |
| **Message Broker** | **Apache Kafka** | 3.x | Cầu nối bất đồng bộ giữa Java (User Actions) và Python (Processing). |
| **Database** | PostgreSQL | 17+ | Lưu trữ bền vững (Social Data). |
| **Caching/Rank** | Redis (ZSET) | 7+ | Lưu trữ BXH, Feed Pools, Tags/Mentions. |
| **Search Engine** | Meilisearch | 1.5+ | Tìm kiếm tốc độ cao (<50ms). |
| **Migration** | Flyway | Latest | Code First DB Migration. |

### 2.2. High-Level Architecture Diagram

```mermaid
graph TD
    User -->|REST API| JavaApp[Java Spring Boot Core]
    
    subgraph "Modular Monolith"
        JavaApp -->|Module| ModCommunity[Community Module]
        JavaApp -->|Module| ModSocial[Social Module]
        JavaApp -->|Module| ModFeed[Feed Module]
        JavaApp -->|Module| ModComment[Comment Module]
    end

    JavaApp -->|Async Events| Kafka{Apache Kafka}
    Kafka -->|Consume Interactions| PyWorker[Python Worker]
    
    PyWorker -->|Calculate Score| PyWorker
    PyWorker -->|Update Rank| Redis[(Redis ZSET)]
    
    ModFeed -->|Get Top IDs| Redis
    ModFeed -->|Get Tags/Mentions| Redis
    JavaApp -->|Persist Data| DB[(PostgreSQL)]
    JavaApp -->|Sync Search| Meili[(Meilisearch)]
```

---

## 3. THIẾT KẾ CƠ SỞ DỮ LIỆU (DATABASE DESIGN)

### 3.1. Chiến lược ID (ID Strategy)

Sử dụng chiến lược Dual-Key Identification (Định danh kép) để tối ưu hóa cả Hiệu năng máy và Trải nghiệm người dùng.

> [!TIP]
> Chi tiết triển khai hệ thống định danh thông minh (Hybrid: Latinized Prefix + NanoID Suffix) có thể xem tại [user-identity-spec.md](user-identity-spec.md).

**Internal ID (Dùng cho Máy):**
*   **Công nghệ:** TSID (Time-Sorted Unique Identifier).
*   **Kiểu dữ liệu:** BIGINT (64-bit).
*   **Lợi ích:** Tương thích hoàn hảo với B-Tree Index của PostgreSQL, không gây phân mảnh trang (Page Splitting) như UUID, sắp xếp được theo thời gian.

> [!TIP]
> Chi tiết giải pháp kỹ thuật sinh Distributed ID và Redis Auto-Discovery xem tại [tsid-generation-spec.md](tsid-generation-spec.md).

**Public ID (Dùng cho Người):**
*   **User Tag:** `LatinizedName` + `#` + `NanoID`. (Ví dụ: `LL#Xy9z` cho user "李小龙").
*   **Cấu trúc:** Prefix (xử lý bởi ICU4J) giúp dễ đọc + Suffix (NanoID) đảm bảo duy nhất và bảo mật.
*   **Lợi ích:** URL thân thiện, hỗ trợ tìm kiếm toàn cầu, đồng thời bảo vệ quyền riêng tư (Suffix ngẫu nhiên).

### 3.2. Schema & Modules Design

Hệ thống tập trung vào **Social Network** với Community làm trung tâm:

**A. Phân hệ Social (Community-First):**
*   `communities` (id, name, public_id, slug, is_private, is_searchable): Nhóm sinh hoạt chung.
*   `posts` (id, user_id, community_id, series_id, content, slug, viral_score):
    *   `community_id` is NULL -> **Personal Post**.
    *   `community_id` NOT NULL -> **Community Post**.
    *   `series_id` NOT NULL -> **Series Post** (cho Creator).
*   `series` (id, creator_id, title, description): Gom các bài đăng thành tập/chapter.

**B. Comment System (Instagram-Style - Flat):**
*   `comments` (id, post_id, user_id, parent_id, content):
    *   `parent_id` is NULL -> **Comment chính**.
    *   `parent_id` NOT NULL -> **Reply** (chỉ 1 cấp).

**C. Common Identity & Interaction:**
*   `users` (id, public_id, email, ...).
*   `community_members` (user_id, community_id, role): User join community.
*   `user_follows` (follower_id, target_id, created_at):
    *   **Type:** Internal TSID (BIGINT). *Luôn dùng ID nội bộ để join bảng cho nhanh.*
    *   **PK (Composite):** `(follower_id, target_id)`.
*   `saved_posts` (user_id, post_id, saved_at):
    *   **PK (Composite):** `(user_id, post_id)` - Mỗi người chỉ lưu 1 bài 1 lần.

> [!TIP]
> Chi tiết chiến lược URL đẹp (Slug + Short ID) xem tại [url-identity-spec.md](url-identity-spec.md).

---

## 4. TÍNH NĂNG KỸ THUẬT LÕI (CORE ENGINEERING FEATURES)

### 4.1. Adaptive Feed Algorithm (Thuật toán tự thích nghi)

Thuật toán tự động điều chỉnh dựa trên hành vi người dùng:

*   **Lướt nhanh:** Ưu tiên ảnh đẹp, nội dung giải trí (Dopamine).
*   **Dừng lại lâu/Bấm comment:** Chuyển sang "Talk mode", đẩy các bài có thảo luận sôi nổi.
*   **Nguồn cấp:** Trộn lẫn giữa Following và Joined Communities.

### 4.2. Gravity Feed Algorithm (Thuật toán xếp hạng)

Sử dụng công thức Gravity Decay (tương tự HackerNews) để tạo Newsfeed "Trending".

$$Score = \frac{(Votes - 1)}{(Time_{hours} + 2)^{1.8}}$$

> [!IMPORTANT]
> Phiên bản nâng cấp **Heart-Based Ranking** (Thả tim thay cho Vote) và các tín hiệu tương tác nâng cao được mô tả chi tiết tại [ranking-algorithm-spec.md](ranking-algorithm-spec.md).

*   **Cơ chế:**
    *   Khi có Vote mới -> Tính lại Score -> Cập nhật vào Redis Sorted Set (ZSET).
    *   Khi User lướt Feed -> Lấy Top ID từ Redis -> Query chi tiết từ PostgreSQL.
*   **Hiệu năng:** Giảm tải 90% việc sort DB cho PostgreSQL.

### 4.3. Cursor-Based Pagination

Sử dụng cho Feed và Comment để không bị lag khi user "lướt mãi không hết".

*   **Feed:** Dùng `created_at` + `id` làm cursor.
*   **Comment:** Dùng `created_at` + `id` làm cursor, load reply tại chỗ khi bấm "Xem thêm".

### 4.4. Redis Tags/Mentions Management

Quản lý các tag/mention thời gian thực bằng Redis:

*   **Real-time Tags:** Lưu tags đang trending trong Redis ZSET.
*   **Mentions:** Lưu mentions (@username) để notify user ngay lập tức.
*   **Cache:** Cache kết quả search tags/mentions để giảm tải DB.

### 4.5. Sensitive Content Control (NSFW System)

Hệ thống hỗ trợ kiểm soát nội dung nhạy cảm (18+) cho Web Platform.

*   **User Settings:** Cho phép User bật/tắt chế độ xem nội dung nhạy cảm.
*   **Content Labeling:** Gắn cờ `is_nsfw` cho Community và Post.
*   **View Logic:** Hiển thị mờ (Blur) và cảnh báo nếu User chưa bật setting.

> [!TIP]
> Xem chi tiết luồng xử lý và thiết kế DB tại [sensitive-content-control-spec.md](sensitive-content-control-spec.md).

### 4.6. Tag-Based Classification System

Thay thế cấu trúc Forum cứng nhắc bằng hệ thống Tags linh hoạt.

*   **System Tags:** Admin định nghĩa danh mục lớn (Technology, Funny, NSFW).
*   **User Tags:** User tự tạo hashtag (#hanoi, #drama).
*   **Contextual Search:** Tìm kiếm kết hợp (Tag bài viết + Tag cộng đồng).

> [!TIP]
> Chi tiết xem tại [tag-based-classification-spec.md](tag-based-classification-spec.md).

### 4.7. High-Performance Search Engine

Sử dụng **Meilisearch** để cung cấp khả năng tìm kiếm tức thì (<50ms).

*   **Features:** Typo tolerance, Faceted Search (lọc theo Tag/NSFW), Sorting.
*   **Sync Strategy:** Hybrid CQRS (Async sync từ PostgreSQL -> Meilisearch).
*   **Search Scope:** Title, Content Preview, Tags, Author, Community.

> [!TIP]
> Xem chi tiết cấu hình Index và API tại [search-engine-spec.md](search-engine-spec.md).

### 4.8. URL Identity System (SEO Friendly)

Hệ thống sử dụng cơ chế **Slug + Short ID** để tạo URL thân thiện và bền vững.

*   **Format:** `/p/{readable-slug}.{short-id}` (VD: `/p/yeu-meo.Xy9z`).
*   **Logic:** Hệ thống query bằng Short ID (Unique), bỏ qua Slug.
*   **Canonical:** Tự động Redirect 301 nếu Slug trên URL sai lệch so với Slug trong DB.

> [!TIP]
> Xem chi tiết thuật toán sinh Short ID và cấu hình Router tại [url-identity-spec.md](url-identity-spec.md).

### 4.9. Creator Features (Series, Zero Compression, Watermark)

**Series System:**
*   Cho phép creator gom các bài đăng thành tập/chapter.
*   User có thể lướt xem trọn bộ bằng viewer chuyên dụng.

**Zero Compression:**
*   Không nén ảnh để giữ nguyên chất lượng tác phẩm.
*   Hỗ trợ độ phân giải cao nhất.

**Watermark:**
*   Tích hợp tính năng gắn Watermark tự động để bảo vệ bản quyền.

**Bot/Spam Filtering:**
*   Cơ chế riêng để dọn sạch rác, quảng cáo trong comment.
*   Chỉ giữ lại tương tác thật của con người.

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
