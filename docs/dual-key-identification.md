# TECHNICAL SPECIFICATION: DUAL-KEY IDENTIFICATION & SMART USER TAGS

**Project:** ThreadIt Forum Backend  
**Author:** LongDx  
**Version:** 1.0  
**Status:** Approved

---

## 1. Bối cảnh & Vấn đề (Context & Problem)

Trong quá trình thiết kế hệ thống định danh người dùng (User Identity), chúng ta đối mặt với các thách thức sau:

- **Quyền tự do đặt tên (Naming Freedom):** Người dùng muốn đặt tên hiển thị (Display Name) tùy ý (trùng nhau, dùng ký tự đặc biệt, Teencode, Emoji, tiếng Trung/Nhật/Hàn...).

- **Khả năng tìm kiếm toàn cầu (Global Searchability):** Nếu User đặt tên là chữ tượng hình (ví dụ: 甘米らくれ), người dùng quốc tế hoặc thiết bị không hỗ trợ font sẽ không thể gõ hoặc tìm kiếm được user đó.

- **Hiệu năng Database (Performance):** Việc tìm kiếm bằng String (username) chậm hơn nhiều so với tìm kiếm bằng số (ID).

- **Thẩm mỹ & Bảo mật (Aesthetics & Privacy):** Không muốn lộ ID dạng số thứ tự (user/1, user/2) gây cảm giác thiếu chuyên nghiệp và dễ bị cào dữ liệu (enumeration attack).

---

## 2. Giải pháp Kiến trúc (Architecture Solution)

Chúng tôi áp dụng chiến lược **Dual-Key (Định danh kép)** kết hợp với thuật toán **Latinh hóa (Romanization)**.

### 2.1. Cấu trúc ID

Mỗi User sẽ có 2 tầng định danh:

| Loại ID | Kiểu dữ liệu | Mô tả | Mục đích |
|---------|--------------|-------|----------|
| **Internal ID** | Long (TSID) | ID số ngẫu nhiên có sắp xếp (Snowflake style). | Dùng làm Primary Key trong DB, Join bảng, Indexing. Tốc độ truy vấn tối đa. |
| **Public Tag** | String | Format: Initials + # + Hash. | Dùng để hiển thị trên UI, URL, chia sẻ profile và tìm kiếm bạn bè. |

### 2.2. Format Public Tag

Public Tag được sinh ra tự động từ Display Name và Internal ID theo công thức:

$$PublicTag = LatinInitials(DisplayName) + "\#" + Base62(InternalID)$$

**Ví dụ:** User tên "李小龙" (Lý Tiểu Long) có ID 4810293 -> Tag: `LL#7x9A`

---

## 3. Thuật toán xử lý (Algorithm Detail)

### 3.1. Xử lý phần Initials (Tiền tố) - "The Latinizer"

Để giải quyết bài toán "Chữ tượng hình", hệ thống sử dụng thư viện **IBM ICU4J** để chuyển đổi mọi ngôn ngữ về ký tự Latin (A-Z).

**Bước 1 - Transliteration:** Dùng bộ dịch Any-Latin; Latin-ASCII để phiên âm.
- 李 -> Li
- 甘 -> Gan
- Nguyễn -> Nguyen

**Bước 2 - Sanitization:** Loại bỏ toàn bộ ký tự đặc biệt (!@#$%^&*()), chỉ giữ lại chữ cái và số.
- `User!@#` -> `User`
- `꧁༺Gấu༻꧂` -> `Gau`

**Bước 3 - Extraction:** Lấy chữ cái đầu của từ đầu tiên + chữ cái đầu của từ cuối cùng.
- `Li Xiao Long` -> `LL`
- `Gau` -> `G`

**Fallback:** Nếu tên toàn Emoji hoặc ký tự không thể dịch (😭😭😭), hệ thống mặc định tiền tố là `"USER"`.

### 3.2. Xử lý phần Suffix (Hậu tố) - "The Shortener"

Để đảm bảo tính duy nhất (Unique) mà vẫn ngắn gọn:

- **Đầu vào:** TSID (Long - 18 chữ số).
- **Xử lý:** Mã hóa Base62 (0-9, a-z, A-Z).
- **Kết quả:** Chuỗi Hash ngắn (khoảng 10-11 ký tự).

---

## 4. Bảng mô phỏng dữ liệu (Data Simulation)

Dưới đây là kết quả thực tế khi áp dụng thuật toán:

| Input (Display Name) | Transliteration (IBM ICU) | Initials | Hash Suffix | FINAL PUBLIC TAG |
|---------------------|---------------------------|----------|-------------|------------------|
| John Marston | John Marston | JM | 7x9A | `JM#7x9A` |
| Nguyễn Văn A | Nguyen Van A | NA | 7x9A | `NA#7x9A` |
| 李小龙 (Trung) | Li Xiao Long | LL | 7x9A | `LL#7x9A` |
| 甘米らくれ (Nhật) | Gan Mi rakure | GR | 7x9A | `GR#7x9A` |
| User!!!123 | User123 | U3 | 7x9A | `U3#7x9A` |
| 😭😭😭 | (Empty) | USER | 7x9A | `USER#7x9A` |

---

## 5. Luồng xử lý API (Technical Implementation Flow)

### 5.1. Khi lưu vào Database (Write)

- Chỉ lưu **Internal ID (Long)** và **Display Name (String UTF-8)**.
- **KHÔNG lưu Public Tag vào DB** để tiết kiệm dung lượng và tránh dư thừa dữ liệu (Redundancy). Tag là thuộc tính được tính toán động (Computed Property).

### 5.2. Khi truy vấn (Read/Search)

Khi Client gọi API `GET /api/v1/users/{userTag}` với input là `LL-7x9A` (hoặc `LL#7x9A`):

1. **Parse:** Backend cắt chuỗi, lấy phần Hash sau ký tự cuối cùng (`7x9A`).
2. **Decode:** Giải mã Base62 `7x9A` -> `4810293` (Internal ID gốc).
3. **Query:** Gọi `userRepository.findById(4810293)`.
   - **Ưu điểm:** Tốc độ truy vấn là O(1) nhờ tìm kiếm theo Primary Key. Không cần Full-text search, không sợ chậm khi DB lớn.
4. **Response:** Trả về User Profile.

---

## 6. Lợi ích (Business & Tech Value)

- **User Experience (UX):** Tôn trọng sự tự do của người dùng. Họ có thể đặt tên hiển thị tùy thích mà vẫn có một định danh "sạch", dễ nhớ, dễ gõ để chia sẻ.

- **Global Access:** Giải quyết triệt để rào cản ngôn ngữ. Một người dùng Mỹ có thể dễ dàng add friend một người dùng Nhật Bản thông qua Public Tag Latin (`GR#...`) mà không cần cài bàn phím tiếng Nhật.

- **Performance Optimization:** Hệ thống bề ngoài dùng String ID (Tag), nhưng bên dưới hoàn toàn chạy bằng ID số (Long). Tối ưu hóa tuyệt đối cho Indexing và Joins của PostgreSQL.

- **URL SEO Friendly:** URL sạch sẽ, không chứa ký tự đặc biệt (`forum.com/u/LL-7x9A`).

---

## 7. Dependencies Required

```xml
<dependency>
    <groupId>com.ibm.icu</groupId>
    <artifactId>icu4j</artifactId>
    <version>74.2</version>
</dependency>

<dependency>
    <groupId>com.github.f4b6a3</groupId>
    <artifactId>tsid-creator</artifactId>
</dependency>
```

