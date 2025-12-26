# TECHNICAL SPECIFICATION: URL IDENTITY SYSTEM (SLUG + SHORT ID)

**Project:** Social Media Backend  
**Module:** Routing & SEO  
**Version:** 1.0  
**Status:** Approved  
**Pattern:** Dot Separator (`slug.public_id`)

---

## 1. NGUYÊN LÝ HOẠT ĐỘNG (CORE CONCEPT)

Thay vì dùng Internal ID (số tự tăng) hoặc Slug thuần túy (dễ trùng) trên URL, hệ thống sử dụng cơ chế lai:

**URL = {Readable Slug} + . + {Short Unique ID}**

*   **Readable Slug:** Dùng cho SEO và mắt người đọc. Hệ thống **không dùng** chuỗi này để query DB.
*   **Separator:** Dấu chấm (`.`).
*   **Short Unique ID:** Chuỗi NanoID ngắn (8-10 ký tự). Hệ thống dùng chuỗi này để tìm kiếm bản ghi.

### Ví dụ minh họa:
*   **Group:** `myapp.com/g/hoi-yeu-meo.Xy9z`
*   **Forum (Community):** `myapp.com/f/phan-cung-may-tinh.A1b2`
*   **Thread/Post:** `myapp.com/t/huong-dan-build-pc.M9kL2`

---

## 2. THIẾT KẾ DATABASE (SCHEMA ADJUSTMENT)

Cần cập nhật bảng `GROUPS` (Communities) và `POSTS` để hỗ trợ Short ID.

### 2.1. Bảng COMMUNITIES / GROUPS
```sql
ALTER TABLE communities 
    -- 1. Thêm cột public_id (Short ID)
    ADD COLUMN public_id VARCHAR(10),
    
    -- 2. Xóa ràng buộc Unique ở slug (vì slug chỉ để hiển thị, trùng cũng được)
    DROP CONSTRAINT communities_slug_key, 
    
    -- 3. Đánh Unique Index cho public_id (BẮT BUỘC)
    ADD CONSTRAINT uq_communities_public_id UNIQUE (public_id);

-- 4. Tạo Index để search cho nhanh
CREATE INDEX idx_communities_public_id ON communities(public_id);
```

### 2.2. Bảng POSTS
```sql
ALTER TABLE posts 
    ADD COLUMN public_id VARCHAR(12) UNIQUE, -- Post số lượng nhiều nên để 12 ký tự cho an toàn
    ADD COLUMN slug VARCHAR(350); -- Lưu slug của tiêu đề (để đỡ phải convert mỗi lần render)

CREATE INDEX idx_posts_public_id ON posts(public_id);
```

---

## 3. THUẬT TOÁN SINH SHORT ID (BACKEND)

Không dùng NanoID mặc định (21 ký tự) vì quá dài cho URL. Chúng ta dùng **Custom Alphabet NanoID** với độ dài 8-10 ký tự.

### Quy tắc:
*   **Độ dài Group ID:** 8 ký tự (đủ cho ~200 nghìn tỷ ID mà xác suất trùng < 1%).
*   **Độ dài Post ID:** 10-12 ký tự.
*   **Bảng ký tự:** Bỏ các ký tự dễ gây nhầm lẫn (`l`, `1`, `O`, `0`, `-`, `_`). Chỉ dùng chữ cái và số để URL nhìn sạch.

### Implementation Guide (Java)
```java
import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import java.security.SecureRandom;

public class ShortIdGenerator {
    // Bảng ký tự sạch (58 ký tự), bỏ l, 1, O, 0, -, _
    private static final char[] ALPHABET = 
        "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz".toCharArray();
    
    private static final int GROUP_ID_LENGTH = 8;
    private static final int POST_ID_LENGTH = 10;

    public static String generateGroupId() {
        return NanoIdUtils.randomNanoId(new SecureRandom(), ALPHABET, GROUP_ID_LENGTH);
    }

    public static String generatePostId() {
        return NanoIdUtils.randomNanoId(new SecureRandom(), ALPHABET, POST_ID_LENGTH);
    }
}
```

---

## 4. XỬ LÝ URL (ROUTING & PARSING)

### 4.1. Logic tách ID (Parsing)
Khi Request gửi lên: `GET /g/hoi-yeu-meo.Xy9z`

1.  Backend nhận tham số path variable: `fullSlug = "hoi-yeu-meo.Xy9z"`.
2.  Tìm vị trí dấu chấm cuối cùng (`Last Index Of .`).
3.  Tách chuỗi:
    *   `slug_part = "hoi-yeu-meo"` (Bỏ qua).
    *   `id_part = "Xy9z"` (Dùng cái này query).

**Java Example:**
```java
public Community getCommunityByUrl(String fullSlug) {
    int lastDotIndex = fullSlug.lastIndexOf('.');
    
    if (lastDotIndex == -1 || lastDotIndex == fullSlug.length() - 1) {
        throw new BadRequestException("URL không hợp lệ");
    }

    // Tách lấy ID
    String publicId = fullSlug.substring(lastDotIndex + 1);
    
    // Query DB bằng ID
    return communityRepository.findByPublicId(publicId)
        .orElseThrow(() -> new NotFoundException("Không tìm thấy nhóm"));
}
```

### 4.2. Logic SEO (Canonical Check - Nâng cao)
Để tối ưu SEO 100%, hệ thống sẽ check tính nhất quán của Link.

*   **Kịch bản:** Nhóm đổi tên từ "Yêu Mèo" -> "Yêu Chó".
    *   Slug cũ: `yeu-meo`. Short ID: `Xy9z`.
    *   Slug mới: `yeu-cho`. Short ID: `Xy9z`.
*   **User vào link cũ:** `.../yeu-meo.Xy9z`
1.  Backend tìm thấy nhóm `Xy9z`.
2.  Backend so sánh slug trên URL (`yeu-meo`) với slug hiện tại trong DB (`yeu-cho`).
3.  Thấy khác nhau -> Thực hiện **Redirect 301 (Moved Permanently)** sang link mới `.../yeu-cho.Xy9z`.

---

## 5. QUY HOẠCH URL (ROUTING TABLE)

| Loại tài nguyên | Pattern URL | Ví dụ | Ghi chú |
| :--- | :--- | :--- | :--- |
| **Community / Group** | `/c/{slug}.{id}` | `/c/meme-vn.B2kL` | Hoặc `/g/` tùy preference. |
| **Section (Topic)** | `/s/{slug}.{id}` | `/s/phan-cung.A8x9` | Tương đương Sub-forum cũ. |
| **Post / Thread** | `/t/{slug}.{id}` | `/t/cach-cai-win.M9p2` | |
| **User Profile** | `/u/{handle}` | `/u/longdx` | User vẫn dùng Handle riêng (hoặc Public ID nếu muốn). |

---
*End of Specification.*
