# TECHNICAL SPECIFICATION: USER IDENTITY & SMART LOGIN SYSTEM

**Project:** ThreadIt Forum Platform  
**Module:** Core Identity (Auth)  
**Version:** 2.2 (Hybrid Model: Latin + NanoID)  
**Author:** LongDx  
**Last Updated:** December 2025

---

## 1. TỔNG QUAN (OVERVIEW)

Module này giải quyết bài toán định danh người dùng theo tiêu chuẩn hiện đại, cân bằng giữa tính Tiện lợi (UX), Thẩm mỹ và Bảo mật.

### Mục tiêu thiết kế
*   **User Tag:** Định danh "thân thiện" (Human-readable) giúp user dễ dàng tìm kiếm và chia sẻ.
*   **Privacy:** Sử dụng mã ngẫu nhiên (NanoID) làm hậu tố để đảm bảo bảo mật và tránh đoán ID.
*   **Freedom:** Re-rollable User Tag (Đổi mã định danh nếu bị lộ).

---

## 2. THIẾT KẾ DATABASE (BẢNG USERS)

| Tên trường | Kiểu dữ liệu | Index | Mô tả |
| :--- | :--- | :--- | :--- |
| **internal_id** | BIGINT | PK | ID nội bộ (TSID). Dùng để Join bảng. |
| **public_id** | VARCHAR(20) | UNIQUE | Chứa chuỗi NanoID (Suffix). Ví dụ: `Xy9zQ2mP`. |
| **display_name** | VARCHAR(255) | | Tên hiển thị xã hội. |
| **email** | VARCHAR | UNIQUE | Thông tin Login. |

> [!NOTE]
> Cột `public_id` chỉ lưu phần Suffix. Phần Prefix (Latinized) được tính toán động từ `display_name` khi cần hiển thị User Tag (`Prefix#Suffix`).

---

## 3. THUẬT TOÁN SINH USER TAG (HYBRID ALGORITHM)

Kết hợp **IBM ICU4J** (Latin hóa) và **NanoID** (Random).

### Logic `generateUserTag(displayName)`
1.  **Input:** "李小龙" (Lý Tiểu Long)
2.  **Bước 1 - Prefix (Latinizer):**
    *   Transliterate: `Li Xiao Long`
    *   Initials: `LL`
3.  **Bước 2 - Suffix (NanoID):** Sinh chuỗi ngẫu nhiên 8-12 ký tự: `Xy9zQ2mP`
4.  **Kết quả (User Tag):** `LL#Xy9zQ2mP`

**Implementation Note (Java):**
*   **Prefix:** `UserUtils.getLatinInitials(displayName)` (Dùng ICU4J).
*   **Suffix:** `NanoIdUtils.randomNanoId()`.

---

## 4. API SPECIFICATION (RESTful)

### 4.1. Register / Get Profile
`GET /api/v1/users/me`

**Response:**
```json
{
  "id": 48291023,
  "displayName": "Lý Tiểu Long",
  "publicId": "Xy9zQ2mP",      // Suffix (Lưu trong DB)
  "userTag": "LL#Xy9zQ2mP"      // Computed (DisplayName -> Latin + PublicId)
}
```

### 4.2. Search User
`GET /api/v1/users/search?q=LL#Xy9z`
*   **Logic:**
    1.  Parse chuỗi query, tách lấy phần sau `#` -> `Xy9z`.
    2.  Query DB: `WHERE public_id = 'Xy9z'`.
    3.  (Optional) So khớp lại Prefix để confirm.

---

## 5. BẢO MẬT & RÀNG BUỘC
*   **NanoID Unique:** Bắt buộc Unique Index trên cột `public_id`.
*   **Re-roll:** User có thể request đổi `public_id` mới -> User Tag mới sẽ là `LL#NewCode`.

---
*End of Specification.*
