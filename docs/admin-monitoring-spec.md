# TECHNICAL SPECIFICATION: ADMIN & MONITORING SYSTEM

**Project:** ThreadIt Forum Platform  
**Module:** Management & Observability  
**Version:** 1.0  
**Author:** LongDx  
**Last Updated:** December 2025

---

## 1. KIẾN TRÚC TỔNG QUAN (OVERVIEW)

Hệ thống Admin được tách biệt khỏi User Frontend để đảm bảo bảo mật và hiệu năng tối ưu.

*   **Admin CMS (Web Portal):** Dành cho Mod/Admin quản lý nội dung và người dùng.
*   **Monitoring Stack (Infrastructure):** Dành cho Dev/DevOps theo dõi sức khỏe hệ thống.

---

## 2. TECH STACK ĐỀ XUẤT

*   **Business Admin FE:** ReactJS + Ant Design Pro (hoặc Refine.dev).
    *   *Lý do:* Sử dụng Template có sẵn bảng biểu, form CRUD giúp tiết kiệm 80% thời gian phát triển UI.
*   **Monitoring Stack:**
    *   **Spring Boot Actuator:** Expose metrics từ ứng dụng Java.
    *   **Prometheus:** Thu thập dữ liệu metrics (Database dạng Time-series).
    *   **Grafana:** Hiển thị biểu đồ trực quan (Dashboard).

---

## 3. CÁC CHỨC NĂNG & BIỂU ĐỒ CHI TIẾT

### 3.1. Business Dashboard (Quản lý vận hành)

#### Biểu đồ tổng quan (Overview Charts)
*   **New Users Growth:** Line chart (User mới theo ngày/tuần).
*   **Traffic Source:** Pie chart (Direct, Search, Social Share).
*   **Activity Heatmap:** Biểu đồ nhiệt thể hiện khung giờ người dùng online nhiều nhất.

#### Công cụ kiểm duyệt (Moderation Tools)
*   **Report Queue:** Danh sách bài viết bị báo xấu (sắp xếp theo số lượng report giảm dần).
*   **User Management:** Ban/Unban user, xem lịch sử vi phạm.
*   **Content Takedown:** Công cụ xóa bài viết vi phạm (Soft delete).

### 3.2. System Dashboard (Giám sát kỹ thuật - Grafana)

#### Application Health
*   **JVM Memory:** Theo dõi Heap usage để phát hiện sớm Memory Leak.
*   **CPU Usage:** % CPU tiêu thụ của ứng dụng/container.
*   **Active Threads:** Số lượng luồng đang thực thi (phát hiện tình trạng tắc nghẽn).

#### Performance Metrics
*   **Throughput:** Tần suất request (Requests per second - RPS).
*   **Latency:** Thời gian phản hồi trung bình (p95, p99).
*   **Error Rate:** Tỉ lệ lỗi hệ thống (HTTP 4xx/5xx).

---
*End of Specification.*
