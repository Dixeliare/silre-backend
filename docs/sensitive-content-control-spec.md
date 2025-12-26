# FEATURE SPECIFICATION: SENSITIVE CONTENT CONTROL (NSFW SYSTEM)

**Project:** ThreadIt Forum Platform  
**Module:** Content Moderation & User Settings  
**Version:** 1.0  
**Scope:** Web Platform  
**Status:** Approved

---

## 1. MỤC TIÊU (OBJECTIVE)

Cho phép User kiểm soát việc hiển thị nội dung nhạy cảm (18+, NSFW) và gắn nhãn các cộng đồng/bài viết có nội dung này.

---

## 2. THIẾT KẾ DATABASE (SCHEMA CHANGES)

Cần cập nhật 2 bảng: `users` (lưu cấu hình xem) và `communities`/`posts` (phân loại nội dung).

### 2.1. Bảng USERS (Cài đặt cá nhân)

Thêm trường để User quyết định có muốn xem 18+ hay không.

| Tên trường | Kiểu dữ liệu | Default | Mô tả |
| :--- | :--- | :--- | :--- |
| `settings_display_sensitive_media` | BOOLEAN | `FALSE` | **FALSE:** Che/Làm mờ nội dung 18+. <br> **TRUE:** Hiển thị trần trụi (Show all). |

### 2.2. Bảng COMMUNITIES / POSTS (Phân loại nội dung)

Gắn cờ cho Group/Forum hoặc bài viết cụ thể.

| Tên trường | Kiểu dữ liệu | Default | Mô tả |
| :--- | :--- | :--- | :--- |
| `is_nsfw` | BOOLEAN | `FALSE` | Cờ đánh dấu cộng đồng/bài viết này chứa nội dung người lớn. |

---

## 3. LUỒNG NGHIỆP VỤ (LOGIC FLOW)

### 3.1. Logic Hiển thị (View Logic)

Khi Frontend gọi API lấy danh sách bài viết hoặc vào chi tiết Group:

1.  **Backend:** Trả về dữ liệu bài viết kèm theo cờ `is_nsfw`.
2.  **Frontend:** Kiểm tra `user.settings_display_sensitive_media`.

**Trường hợp 1: User đang tắt chế độ 18+ (Default)**
*   Nếu gặp bài viết/group có `is_nsfw = true`:
    *   **Hành động:** Hiển thị Overlay (Lớp phủ) làm mờ ảnh/video.
    *   **UI:** Hiện cảnh báo: *"Nội dung này có thể nhạy cảm. [Hiển thị] / [Vào Cài đặt]"*.
    *   User bấm "Hiển thị" -> Chỉ mở khóa nội dung đó tạm thời (Session state).

**Trường hợp 2: User đã bật chế độ 18+**
*   **Hành động:** Hiển thị ảnh/video bình thường, không che chắn.

### 3.2. Logic Đăng bài (Creation Logic)

Khi User tạo Group hoặc đăng bài trong Group Private:
1.  Cung cấp một checkbox: `[ ] Mark this community/post as NSFW (18+)`.
2.  Nếu Group đã set `is_nsfw = true` -> Mọi bài viết bên trong mặc định kế thừa `is_nsfw = true`.

---

## 4. UI/UX SPECIFICATION (WEB)

### 4.1. User Settings Page
Trong trang `settings/privacy`:
*   **Label:** "Nội dung nhạy cảm" (Sensitive Content).
*   **Toggle Switch:** "Hiển thị nội dung có thể nhạy cảm" (Display media that may contain sensitive content).
*   **Mô tả:** "Bật tùy chọn này để xem ảnh/video 18+ mà không bị che."

### 4.2. Content Card (Khi bị che)
Khi render một bài viết NSFW cho user chưa bật setting:
*   **Container:** Màu xám hoặc hiệu ứng Blur (`CSS filter: blur(20px)`).
*   **Icon:** Con mắt bị gạch chéo hoặc icon cảnh báo (!).
*   **Text:** "Nội dung nhạy cảm".
*   **Button:** "Xem ảnh" (Click vào thì remove class blur).

---

## 5. API RESPONSE MODEL (JSON Example)

Khi Client request lấy thông tin bài viết: `GET /api/posts/{id}`

```json
{
  "id": "post_123",
  "content": "Đây là ảnh nghệ thuật...",
  "media_url": "https://cdn.yourapp.com/img_xxx.jpg",
  "is_nsfw": true,  // <-- Frontend dựa vào đây để xử lý Blur
  "author": {
    "handle": "user_abc"
  }
}
```

---

## 6. LƯU Ý QUAN TRỌNG (COMPLIANCE)

Dù Web thoáng hơn, nhưng để tránh bị report domain hoặc bị chặn bởi các nhà cung cấp dịch vụ, bạn nên:

1.  **SEO Meta Tags:** Với các trang public có nội dung 18+, nên thêm thẻ meta:
    ```html
    <meta name="rating" content="adult" />
    ```
    (Điều này giúp Google không index ảnh 18+ của bạn vào kết quả tìm kiếm an toàn, tránh bị đánh gậy SEO).

2.  **Terms of Service:** Ghi rõ User chịu trách nhiệm về nội dung họ đăng tải.
