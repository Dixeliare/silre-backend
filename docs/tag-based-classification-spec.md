# FEATURE SPECIFICATION: TAG-BASED CLASSIFICATION SYSTEM

**Project:** Social Media Backend  
**Module:** Content Organization & Discovery  
**Version:** 1.0  
**Status:** Approved

---

## 1. MỤC TIÊU & PHẠM VI (OBJECTIVE & SCOPE)

Áp dụng cho **Phân hệ Social (Community & Personal)**.
Cung cấp hệ thống **Tags** linh hoạt (tương tự Manga/Twitter/Tumblr) để tối ưu hóa khả năng tìm kiếm và gợi ý nội dung, bổ sung cho cấu trúc Sub-forum truyền thống bên phân hệ Forum.

---

## 2. PHÂN LOẠI TAGS (TAG CATEGORIZATION)

Hệ thống chia Tags làm 2 loại chính để quản lý:

### 2.1. System Tags (Tags hệ thống)
*   **Định nghĩa:** Các Tag do Admin tạo và quản lý.
*   **Mục đích:** Định hình các "Sân chơi" hoặc "Danh mục lớn" (Category).
*   **Quy tắc:** Communities bắt buộc phải chọn ít nhất 1 System Tag khi tạo.
*   **Ví dụ:** `Technology`, `News`, `Entertainment`, `NSFW`, `Politics`.

### 2.2. User Tags (Hashtags)
*   **Định nghĩa:** Tag do User tự tạo ngẫu hứng (Folksonomy).
*   **Mục đích:** Mô tả chi tiết nội dung, trend nhất thời.
*   **Quy tắc:** Tự do thêm vào bài viết hoặc mô tả nhóm.
*   **Ví dụ:** `#hanoi`, `#rainy_day`, `#drama`, `#review`.

---

## 3. QUY TẮC SEARCH & FEED

### 3.1. Basic Search
Query: `"Marvel"`

1.  **Communities Search:** Tìm trong bảng Communities có `System Tag` hoặc `User Tag` là "Marvel".
2.  **Posts Search:** Tìm trong bảng Posts có hashtag `#Marvel` hoặc nội dung chứa từ khóa "Marvel".

### 3.2. Contextual Search (Lọc chéo)
Cho phép User kết hợp nhiều điều kiện để tìm kiếm chính xác (Advanced Filter).

*   **Logic:** `Posts` with Tag `#A` **AND** inside `Communities` with Tag `[B]`.
*   **Ví dụ:** Tìm bài viết có tag `#Review` NHƯNG phải nằm trong các Group có tag `[Phim Ảnh]`.
    *   Query: `tag:Review AND community_tag:Movie`

---

## 4. DATABASE DESIGN (CONCEPTUAL)

### 4.1. Table `tags`
| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | BIGINT | TSID |
| `slug` | VARCHAR | Unique (e.g., `technology`, `hanoi`) |
| `name` | VARCHAR | Display Name (e.g., "Technology", "Hà Nội") |
| `type` | ENUM | `SYSTEM` or `USER` |

### 4.2. Table `community_tags` / `post_tags`
Bảng trung gian (Many-to-Many) để map Tags với Entities.

---

## 5. API ENDPOINTS (DRAFT)

*   `GET /api/tags/system` - Lấy danh sách System Tags (cho màn hình tạo Group).
*   `GET /api/search?q=Marvel&type=post&context=Movie` - Tìm kiếm theo ngữ cảnh.

---
*End of Specification.*
