# TECHNICAL SPECIFICATION: HIGH-PERFORMANCE SEARCH & TAGGING SYSTEM

**Project:** Social Media Backend  
**Module:** Search Engine  
**File Name:** SEARCH_ENGINE_SPEC.md  
**Version:** 1.0  
**Date:** 26/12/2025  
**Technology:** Meilisearch, PostgreSQL  
**Status:** Approved

---

## 1. MỤC TIÊU (OBJECTIVE)

Xây dựng hệ thống tìm kiếm tốc độ cao (<50ms), hỗ trợ tìm kiếm mờ (typo tolerance) và bộ lọc đa chiều (Faceted Search) theo Tags.

---

## 2. KIẾN TRÚC HỆ THỐNG (ARCHITECTURE)

Chúng ta áp dụng mô hình **CQRS lai (Hybrid CQRS)** để tách biệt việc Ghi và Đọc dữ liệu tìm kiếm.

### 2.1. Phân chia trách nhiệm
*   **Write Model (PostgreSQL):** Nguồn dữ liệu chính (Source of Truth). Chịu trách nhiệm lưu trữ, toàn vẹn dữ liệu, quan hệ (Relations).
*   **Read Model (Meilisearch):** Nguồn dữ liệu tra cứu. Dữ liệu được "làm phẳng" (Denormalized) thành JSON để tối ưu tốc độ đọc.

### 2.2. Luồng dữ liệu (Data Flow)
1.  **Create/Update:** User gọi API -> Lưu vào PostgreSQL.
2.  **Sync (Async):** Sau khi Transaction ở Postgres thành công -> Trigger một Event (hoặc Job) đẩy dữ liệu sang Meilisearch.
3.  **Search:** User gọi API tìm kiếm -> Backend query Meilisearch -> Trả về kết quả.

---

## 3. THIẾT KẾ DỮ LIỆU (INDEX SCHEMA)

Trong Meilisearch, dữ liệu lưu trong các **Index** (tương tự Table). Index quan trọng nhất là `posts`.

### Cấu trúc Document (JSON)
Mỗi bài viết khi đẩy sang Meilisearch sẽ có dạng JSON phẳng như sau:

```json
{
  "id": 1005,                       // ID gốc từ Postgres
  "title": "Review phim Ma Cây",
  "slug": "review-phim-ma-cay",
  "content_preview": "Phim này sợ vãi linh hồn, đoạn đầu...", // Chỉ lưu khoảng 200 ký tự đầu để hiển thị preview
  "author": {
    "id": 55,
    "name": "Nguyen Van A",
    "handle": "user_abc",           // Dùng để search theo tên người đăng
    "avatar": "url_to_image"
  },
  "community": {
    "id": 10,
    "name": "Hội Mê Phim",
    "slug": "hoi-me-phim",
    "is_nsfw": false
  },
  "tags": [                         // QUAN TRỌNG: Mảng Tags phẳng
    "Review",
    "Kinh Dị",
    "Phim Ảnh",
    "Spoiler"
  ],
  "stats": {
    "likes": 150,
    "comments": 20
  },
  "created_at_timestamp": 1703581200 // Unix Timestamp để sort
}
```

---

## 4. CẤU HÌNH MEILISEARCH (INDEX SETTINGS)

Để Search Engine hoạt động thông minh, cần cấu hình các thuộc tính sau ngay khi khởi tạo Index.

### 4.1. Searchable Attributes (Tìm ở đâu?)
Chỉ định các trường mà User gõ từ khóa sẽ quét qua. Thứ tự quyết định độ ưu tiên.

```json
[
  "title",
  "tags",
  "community.name",
  "author.name",
  "author.handle",
  "content_preview"
]
```

### 4.2. Filterable Attributes (Lọc theo cái gì?)
Dùng cho Sidebar filter (Tags, NSFW switch). Bắt buộc phải khai báo mới lọc được.

```json
[
  "tags",                   // Cho phép lọc theo tags (VD: tags = 'Kinh Dị')
  "community.id",           // Lọc bài trong 1 group cụ thể
  "community.is_nsfw",      // Lọc bỏ nội dung 18+
  "author.id"
]
```

### 4.3. Sortable Attributes (Sắp xếp)
```json
[
  "created_at_timestamp",
  "stats.likes"
]
```

### 4.4. Ranking Rules (Luật xếp hạng)
Giữ nguyên mặc định của Meilisearch là rất tốt (Typo -> Words -> Proximity -> Attribute -> Exactness).

---

## 5. CHIẾN LƯỢC ĐỒNG BỘ (SYNC STRATEGY)

Để đảm bảo dữ liệu luôn tươi mới (Real-time search), ta sử dụng cơ chế **Event-Driven**.

### 5.1. Real-time Sync (Khi có hoạt động mới)
*   **Trigger:** Khi hàm `postRepository.save(post)` chạy xong.
*   **Action:**
    1.  Convert Entity Post -> DTO `PostSearchDocument`.
    2.  Gọi `meiliClient.index("posts").addDocuments(...)`.
*   **Xử lý xóa:** Khi xóa bài -> Gọi `meiliClient.index("posts").deleteDocument(id)`.

### 5.2. Re-indexing (Đồng bộ lại toàn bộ)
Dùng khi schema thay đổi hoặc dữ liệu bị lệch.

*   **Script:** Viết một API Admin `/api/admin/sync-search`.
*   **Logic:**
    1.  Xóa toàn bộ Index cũ trong Meilisearch.
    2.  Fetch toàn bộ Post từ PostgreSQL (phân trang, batch size 1000).
    3.  Đẩy lại vào Meilisearch.

---

## 6. HƯỚNG DẪN TÍCH HỢP (IMPLEMENTATION GUIDE)

### 6.1. Cài đặt Server
Sử dụng Docker Compose để chạy Meilisearch cùng với App.

```yaml
version: '3'
services:
  meilisearch:
    image: getmeili/meilisearch:v1.5
    ports:
      - "7700:7700"
    environment:
      - MEILI_MASTER_KEY=myMasterKey123  # Key bảo mật
    volumes:
      - ./meili_data:/meili_data
```

### 6.2. Code Client (Java/Spring Boot)
Thêm thư viện `meilisearch-java`.

**Model Document:**
```java
public class PostDocument {
    private String id;
    private String title;
    private String contentPreview;
    private List<String> tags; // Quan trọng: List tags
    private CommunityObj community;
    private long createdAtTimestamp;
    // Getters, Setters...
}
```

**Service Search:**
```java
public SearchResult search(String keyword, List<String> tags, boolean allowNsfw) {
    SearchRequest request = new SearchRequest(keyword)
        .setLimit(20);

    // Xây dựng bộ lọc (Filter)
    List<String> filters = new ArrayList<>();
    
    // 1. Filter theo Tags (Logic OR hoặc AND tùy nhu cầu)
    if (tags != null && !tags.isEmpty()) {
        // Ví dụ: tags IN ['Kinh Dị', 'Review']
        String tagFilter = tags.stream()
            .map(t -> "tags = '" + t + "'")
            .collect(Collectors.joining(" OR "));
        filters.add("(" + tagFilter + ")");
    }

    // 2. Filter NSFW (Privacy)
    if (!allowNsfw) {
        filters.add("community.is_nsfw = false");
    }

    request.setFilter(filters.toArray(new String[0]));

    return meiliClient.index("posts").search(request);
}
```

---

## 7. CÁC USE-CASE TÌM KIẾM ĐIỂN HÌNH

| User Action | Query gửi xuống Meilisearch |
| :--- | :--- |
| **Search thường** | `q="Ma Cây"` |
| **Search Tag** | `q="" AND filter="tags = 'Anime'"` |
| **Search kết hợp** | `q="Review" AND filter="tags = 'Kinh Dị' AND community.is_nsfw = false"` |
| **Tìm trong Group** | `q="Luật lệ" AND filter="community.id = 10"` |

---
*End of Specification.*
