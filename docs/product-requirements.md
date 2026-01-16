# PRODUCT REQUIREMENTS & SYSTEM OVERVIEW

**Tên dự án:** Silre - Social Platform  
**Phiên bản:** 2.0  
**Ngày cập nhật:** 15/01/2026

---

## 1. KIẾN TRÚC & ĐỊNH HƯỚNG (CORE LOGIC)

### 1.1. Mô hình kiến trúc
- **Modular Monolith:** Code nhanh, không bị rối mạng/Docker nhưng vẫn phân tách module sạch sẽ.
- **Tách biệt rõ ràng:** Mỗi module độc lập, dễ maintain và scale sau này.

### 1.2. Loại bỏ Forum
**Tuyệt đối không có Forum:** Không có giao diện dạng thread/danh sách bài viết kiểu cũ. Tránh làm rối UI và gãy cảm xúc người dùng.

### 1.3. Community là "Nguồn cấp" (Not a Folder)
- **User "Join" cộng đồng để định hướng thuật toán:** Khi user join một community (ví dụ: Hunter x Hunter), nội dung từ community đó sẽ tự động xuất hiện trong Feed.
- **Nội dung tự tìm đến Feed:** Thay vì bắt User phải mò vào phòng, nội dung từ Community sẽ tự động được đẩy vào Feed của User.
- **Mục tiêu:** Tạo cảm giác thuộc về một nhóm nhưng trải nghiệm vẫn mượt mà như lướt Instagram.

---

## 2. TRẢI NGHIỆM FEED (DISCOVERY & DISCUSSION)

### 2.1. Thuật toán tự thích nghi
- **Không dùng nút chuyển chế độ (Switch):** Thuật toán tự động điều chỉnh dựa trên hành vi người dùng.
- **Lướt nhanh:** Ưu tiên ảnh đẹp, nội dung giải trí (Dopamine) để giữ chân user.
- **Dừng lại lâu/Bấm comment:** Thuật toán hiểu là User muốn "Hóng biến" (Talk mode), bắt đầu đẩy các bài có thảo luận sôi nổi.

### 2.2. Đề xuất nội dung
- **Trộn lẫn:** Giữa người theo dõi (Following) và các cộng đồng đã tham gia (Joined Communities).
- **Discovery:** Ưu tiên nội dung viral từ người lạ để khám phá mới.
- **Personalization:** Feed được cá nhân hóa dựa trên hành vi tương tác.

---

## 3. HỆ THỐNG COMMENT (KIỂU INSTAGRAM - FLAT)

### 3.1. Phân cấp tối đa 2 cấp
- **Comment chính:** Comment gốc của bài post.
- **Reply:** Phản hồi cho comment chính (tối đa 1 cấp).

### 3.2. Trải nghiệm phẳng
- **Không thụt lề sâu:** Khác với Reddit/Threads, UI phẳng và dễ đọc.
- **"Xem thêm" load reply tại chỗ:** Khi bấm "Xem thêm", reply được load ngay tại chỗ để user kéo xuống đọc liên tục (scroll).

### 3.3. Tag tên (@) mạnh mẽ
- **Định danh người được trả lời:** Tận dụng mạnh việc Tag tên (@) để định danh người đang được trả lời.
- **"Vũ khí" tranh luận:** Giúp các cuộc tranh luận/chửi nhau diễn ra kịch tính và dễ theo dõi.

---

## 4. ĐẶC QUYỀN CHO CREATOR (HỌA SĨ MANGA/V.I.P)

Đây là phần để lôi kéo các họa sĩ Nhật và người có tầm ảnh hưởng:

### 4.1. Hệ thống Series
- **Gom bài đăng thành tập/chapter:** Cho phép creator gom các bài đăng lẻ thành một tập/chapter.
- **Trình xem chuyên dụng (Viewer):** User có thể lướt xem trọn bộ bằng viewer thay vì xem từng ảnh rời rạc.

### 4.2. Chất lượng ảnh Gốc (Zero Compression)
- **Không nén ảnh:** Không nén ảnh làm vỡ nét vẽ.
- **Độ phân giải cao nhất:** Cho phép ảnh độ phân giải cao nhất để giữ nguyên chất lượng tác phẩm.

### 4.3. Bảo vệ tác phẩm
- **Watermark tự động:** Tích hợp tính năng gắn Watermark tự động để bảo vệ bản quyền.

### 4.4. Lọc Bot/Spam
- **Cơ chế riêng:** Dùng cơ chế riêng để dọn sạch rác, quảng cáo trong comment.
- **Chỉ giữ tương tác thật:** Chỉ giữ lại tương tác thật của con người.

---

## 5. ĐẶC TẢ BACKEND (DÀNH CHO DEVELOPER)

### 5.1. Database (PostgreSQL)
- **Bảng Posts:** Có `community_id` và `series_id` để phân phối nội dung.
- **Bảng Comments:** Dùng `parent_id` cho reply cấp 2.

### 5.2. Kỹ thuật xử lý
- **Cursor-based Pagination:** Sử dụng cho Feed và Comment để không bị lag khi user "lướt mãi không hết".
- **Redis:** Quản lý các tag/mention thời gian thực.

---

*End of Requirement.*
