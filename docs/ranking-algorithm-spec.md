# TECHNICAL SPECIFICATION: HEART-BASED RANKING ALGORITHM

**Project:** ThreadIt Forum Platform  
**Module:** Feed Service / Recommendation  
**Version:** 1.0  
**Author:** LongDx  
**Last Updated:** December 2025

---

## 1. MÔ HÌNH: POPULARITY-BASED RANKING

**Thay đổi cốt lõi:** Loại bỏ Downvote. Chỉ sử dụng các tín hiệu tích cực (Positive Signals) cộng dồn. Bài viết dở sẽ tự chìm do không được tương tác, thay vì bị người dùng "đạp" xuống.

---

## 2. CÔNG THỨC TOÁN HỌC (RANKING FORMULA)

Vì không có số âm (Downvote), chỉ số **Time Decay** (Độ trễ thời gian) đóng vai trò cực kỳ quan trọng để đảm bảo Newsfeed luôn tươi mới.

$$RankScore = \frac{TotalEngagementScore}{(TimeSincePosted_{hours} + 2)^{1.8}}$$

*   **Tử số:** Tổng điểm tương tác tích lũy.
*   **Mẫu số:** Thời gian trôi qua càng lâu, mẫu số càng lớn, dẫn đến RankScore giảm nhanh (Decay).

---

## 3. HỆ THỐNG ĐIỂM TRỌNG SỐ (WEIGHTED SCORING)

| Loại Hành Vi | Hành động (Action) | Điểm (Score) | Logic hệ Social |
| :--- | :--- | :--- | :--- |
| **Viral (Lan truyền)** | Share (Chia sẻ) | **+50** | Hành vi giá trị nhất để kéo user mới. |
| **Viral (Lan truyền)** | Tag User | **+40** | Lôi kéo người khác vào thảo luận trực tiếp. |
| **Cam kết (Commit)** | Save (Lưu bài) | **+30** | Nội dung hữu ích, có giá trị xem lại. |
| **Thảo luận** | Comment | **+20** | Tạo ra hội thoại (Reply tính điểm thấp hơn). |
| **Cảm xúc** | Thả Tim (Heart) | **+10** | Thay thế Upvote, thể hiện sự yêu thích nhanh. |
| **Tò mò** | Click "Xem thêm" | **+5** | Tiêu đề/Nội dung lôi cuốn. |
| **Tò mò** | Click Ảnh/Video | **+3** | Tương tác với Media. |
| **Giữ chân** | Dwell Time > 5s | **+2** mỗi 5s | Dừng lại đọc (Tối đa 20 điểm/user). |
| **Tiêu cực** | Report (Báo xấu) | **-1000** | Ẩn ngay khỏi feed cá nhân + Chờ Admin duyệt. |

---

## 4. CƠ CHẾ "TÍN HIỆU NGẦM" (IMPLICIT SIGNALS)

Để hạ nhiệt các bài viết rác khi không có nút Downvote:

*   **Thanh lọc nội dung nhạt (Low CTR):** Nếu bài viết có Impressions cao (đã hiển thị nhiều) nhưng tỷ lệ Click/Tim thấp (< 1%), hệ thống tự động nhân hệ số phạt **0.5** vào RankScore.
*   **Report Spike:** Nếu tỷ lệ Report > 1% số lượt xem, bài viết sẽ bị tự động gỡ khỏi Trending ngay lập tức.

---

## 5. LUỒNG DỮ LIỆU (DATA FLOW)

1.  **Frontend:** Thực hiện UI cập nhật ngay lập tức (Optimistic UI) khi người dùng bấm Tim.
2.  **API:** Gửi request ngầm: `POST /api/reaction {type: 'HEART', postId: 123}`.
3.  **Backend:** 
    *   Sử dụng Redis Set/Bloom Filter để check nhanh xem user đã tương tác chưa.
    *   Đẩy event vào **Kafka** để xử lý cộng/trừ điểm không đồng bộ.
4.  **Database:** 
    *   Bảng `posts` lưu `heart_count`.
    *   Bảng `post_reactions` lưu chi tiết để hiển thị trạng thái nút Tim cho user.

---

## 6. CODE MẪU JAVA (RANKING SERVICE)

```java
public double calculateScore(PostMetrics metrics, long hoursAge) {
    double engagementScore = 
          (metrics.getShares() * 50) 
        + (metrics.getTagCounts() * 40)
        + (metrics.getSaves() * 30)
        + (metrics.getComments() * 20)
        + (metrics.getHearts() * 10)  // Dùng Heart thay Upvote
        + (metrics.getExpandClicks() * 5)
        + (metrics.getMediaClicks() * 3)
        + (Math.min(20, metrics.getDwellTimeScore())); // Max 20 điểm dwell time

    // Phạt bài viết có tỷ lệ tương tác quá thấp (Nội dung nhạt)
    if (metrics.getViewCount() > 100) {
        double ctr = (double) metrics.getHearts() / metrics.getViewCount();
        if (ctr < 0.01) { // Dưới 1% người xem thả tim
            engagementScore *= 0.5; // Giảm một nửa điểm
        }
    }

    // Công thức Time Decay
    return engagementScore / Math.pow(hoursAge + 2, 1.8);
}
```

---
*End of Specification.*
