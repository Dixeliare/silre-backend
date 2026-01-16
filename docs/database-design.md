# DATABASE DESIGN DOCUMENTATION

**Project:** Silre - Social Platform Backend  
**Version:** 2.0  
**Author:** LongDx  
**Last Updated:** January 2026

---

## 1. TỔNG QUAN (OVERVIEW)

Thiết kế database cho hệ thống Social Platform, tập trung vào **Community-First Architecture** với các tính năng:

- Dual-Key User Identity (TSID + NanoID)
- **Community là "Nguồn cấp":** User join community để nội dung tự động xuất hiện trong Feed
- Social Posts (Personal & Community Posts)
- Series System (cho Creator - gom bài đăng thành tập/chapter)
- Topics System (giống Threads - Meta)
- Comment System (Instagram-Style - Flat, tối đa 2 cấp)
- NSFW Content Control
- Interaction System (Likes, Comments, Saves, Shares, Follows)
- Ranking Algorithm Support (Gravity Score)
- Cursor-based Pagination Support

**Triết lý thiết kế:**
- **Loại bỏ Forum:** Không có cấu trúc Forum/Thread truyền thống để tránh làm rối UI và UX.
- **Community-First:** Community là nguồn cấp nội dung, không phải "phòng" để user phải vào xem.

---

## 2. CHIẾN LƯỢC ID (ID STRATEGY)

### 2.1. Internal ID (TSID)
- **Kiểu:** `BIGINT` (64-bit)
- **Công nghệ:** TSID (Time-Sorted Unique Identifier)
- **Mục đích:** Dùng cho Join bảng, Index hiệu quả
- **Lợi ích:** Tương thích B-Tree Index, không gây phân mảnh, sắp xếp theo thời gian

### 2.2. Public ID (Short ID)
- **Kiểu:** `VARCHAR(8-12)`
- **Công nghệ:** Custom NanoID (bỏ ký tự dễ nhầm: l, 1, O, 0, -, _)
- **Mục đích:** Dùng cho URL thân thiện (slug.public_id)
- **Ví dụ:** `/p/yeu-meo.Xy9z` → Query bằng `Xy9z`

---

## 3. CẤU TRÚC BẢNG (SCHEMA STRUCTURE)

### 3.1. User Identity & Authentication

#### `users`
Bảng người dùng với Dual-Key Identity.

| Column | Type | Description |
|--------|------|-------------|
| `internal_id` | BIGINT (PK) | TSID - Dùng để Join |
| `public_id` | VARCHAR(20) UNIQUE | NanoID Suffix (chỉ lưu phần sau `#`) |
| `display_name` | VARCHAR(255) | Tên hiển thị (có thể Emoji, CJK) |
| `email` | VARCHAR(255) UNIQUE | Email đăng nhập |
| `password_hash` | VARCHAR(255) | Bcrypt hash |
| `settings_display_sensitive_media` | BOOLEAN | NSFW setting (default: FALSE) |
| `is_private` | BOOLEAN | Private account (cần approval khi follow, default: FALSE) |
| `created_at`, `updated_at`, `last_login_at` | TIMESTAMP | Timestamps |
| `is_active`, `is_verified` | BOOLEAN | Status flags |

**Indexes:**
- `idx_users_public_id` - Tìm kiếm bằng Public ID
- `idx_users_email` - Login
- `idx_users_display_name` - Search

**Note:** Prefix (Latinized) được tính động từ `display_name` bằng ICU4J, không lưu trong DB.

---

### 3.2. Community System (Community-First Architecture)

#### `communities`
Nhóm cộng đồng - **Community là "Nguồn cấp"**, không phải "phòng" để user phải vào xem.

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | TSID |
| `name` | VARCHAR(255) | Tên nhóm |
| `slug` | VARCHAR(255) | URL slug (**không unique**, có thể trùng - chỉ dùng cho SEO) |
| `public_id` | VARCHAR(10) UNIQUE | Short ID cho URL (query bằng cái này) |
| `description` | TEXT | Mô tả |
| `owner_id` | BIGINT (FK) | Reference to `users` |
| `is_private` | BOOLEAN | Private community (cần approval khi join, default: FALSE) |
| `is_searchable` | BOOLEAN | Có thể search/discover (default: TRUE) |
| `is_nsfw` | BOOLEAN | NSFW flag (default: FALSE) |
| `member_count` | INTEGER | Số thành viên (denormalized) |
| `post_count` | INTEGER | Số bài viết (denormalized) |
| `created_at`, `updated_at` | TIMESTAMP | Timestamps |

**Indexes:**
- `idx_communities_public_id` - Tìm bằng Short ID
- `idx_communities_slug` - SEO
- `idx_communities_is_searchable` - Filter searchable communities

**Note:** 
- Slug không unique vì chỉ dùng để SEO. Query thực tế dùng `public_id`.
- **Community là "Nguồn cấp":** Khi user join community, nội dung từ community đó sẽ tự động xuất hiện trong Feed.

#### `community_members`
Thành viên của communities.

| Column | Type | Description |
|--------|------|-------------|
| `community_id` | BIGINT (FK) | Reference to `communities` |
| `user_id` | BIGINT (FK) | Reference to `users` |
| `role` | VARCHAR(50) | MEMBER, MODERATOR, ADMIN |
| `joined_at` | TIMESTAMP | Thời gian tham gia |

**Primary Key:** `(community_id, user_id)`.

**Indexes:**
- `idx_community_members_user` - Lấy communities của user (để build Feed)
- `idx_community_members_community` - Lấy members của community

---

### 3.3. Posts System

#### `posts`
Bài viết Social (Personal hoặc Community Post).

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | TSID |
| `author_id` | BIGINT (FK) | Reference to `users` |
| `community_id` | BIGINT (FK, NULL) | Reference to `communities` (NULL = Personal Post) |
| `series_id` | BIGINT (FK, NULL) | Reference to `series` (cho Creator - gom bài thành tập/chapter) |
| `topic_id` | BIGINT (FK, NULL) | Reference to `topics` (CHỈ dùng cho Personal Posts, NULL = post không có topic) |
| `title` | VARCHAR(255) | Tiêu đề (optional cho social posts) |
| `content` | TEXT | Nội dung bài viết |
| `public_id` | VARCHAR(12) UNIQUE | Short ID cho URL |
| `slug` | VARCHAR(350) | Slug (auto-generated từ title/content, SEO) |
| `is_nsfw` | BOOLEAN | NSFW flag (kế thừa từ community) |

**Stats cho Ranking Algorithm:**
| Column | Type | Description |
|--------|------|-------------|
| `likes_count` | INTEGER | Số lượt like (trọng số: 1) |
| `comments_count` | INTEGER | Số comment (trọng số: 5) |
| `shares_count` | INTEGER | Số share (trọng số: 10) |
| `saves_count` | INTEGER | Số lượt lưu (trọng số: 8) |
| `tags_count` | INTEGER | Số lượt tag bạn bè (trọng số: 6) |
| `caption_expands_count` | INTEGER | Số lượt bấm "Xem thêm" (trọng số: 1) |
| `media_clicks_count` | INTEGER | Số lượt click ảnh/video (trọng số: 2) |
| `dwell_7s_count` | INTEGER | Số lượt ở lại > 7s (trọng số: 4) |
| `viral_score` | DECIMAL(20,10) | Điểm tính từ Gravity Algorithm |

**Logic:**
- `community_id IS NULL` → **Personal Post**
- `community_id IS NOT NULL` → **Community Post**
- `series_id IS NOT NULL` → **Series Post** (cho Creator)
- `topic_id` → CHỈ dùng cho Personal Posts (có thể NULL nếu post không có topic)

**Constraint:**
- `chk_personal_post_topic`: Đảm bảo `topic_id` chỉ dùng cho Personal Posts (`community_id IS NULL`)

**Indexes:**
- `idx_posts_viral_score` - Sắp xếp theo điểm viral
- `idx_posts_community_created` - Lấy bài trong community
- `idx_posts_author_created` - Lấy bài của user
- `idx_posts_series` - Lấy bài trong series
- `idx_posts_created_at` - Cursor-based pagination

#### `series`
Series cho Creator - Gom các bài đăng thành tập/chapter.

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | TSID |
| `creator_id` | BIGINT (FK) | Reference to `users` |
| `title` | VARCHAR(255) | Tên series |
| `description` | TEXT | Mô tả series |
| `public_id` | VARCHAR(12) UNIQUE | Short ID cho URL |
| `slug` | VARCHAR(350) | Slug (SEO) |
| `post_count` | INTEGER | Số bài trong series (denormalized) |
| `created_at`, `updated_at` | TIMESTAMP | Timestamps |

**Indexes:**
- `idx_series_creator` - Lấy series của creator
- `idx_series_public_id` - Tìm bằng Short ID

---

### 3.4. Comment System (Instagram-Style - Flat)

#### `comments`
Bình luận - **Tối đa 2 cấp** (Comment chính và Reply).

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | TSID |
| `post_id` | BIGINT (FK) | Reference to `posts` |
| `author_id` | BIGINT (FK) | Reference to `users` |
| `parent_comment_id` | BIGINT (FK, NULL) | Reference to `comments` (NULL = Comment chính, NOT NULL = Reply) |
| `content` | TEXT | Nội dung comment |
| `likes_count` | INTEGER | Số lượt like comment (denormalized từ `comment_likes`) |
| `created_at`, `updated_at` | TIMESTAMP | Timestamps |

**Logic:**
- `parent_comment_id IS NULL` → **Comment chính**
- `parent_comment_id IS NOT NULL` → **Reply** (chỉ 1 cấp, không có nested reply)

**Indexes:**
- `idx_comments_post_created` - Lấy comments của post (cursor-based pagination)
- `idx_comments_parent` - Lấy replies của comment
- `idx_comments_author` - Lấy comments của user

**Note:** 
- UI phẳng, không thụt lề sâu như Reddit/Threads.
- Khi bấm "Xem thêm", reply được load tại chỗ để user scroll liên tục.

---

### 3.5. Topics System (Giống Threads - Meta)

#### `topics`
Topics được gán cho Posts (giống Threads của Meta).

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | TSID |
| `name` | VARCHAR(255) UNIQUE | Tên topic (e.g., "Technology", "Hà Nội") |
| `slug` | VARCHAR(255) UNIQUE | URL slug (e.g., "technology", "hanoi") |
| `description` | TEXT | Mô tả topic |
| `image_url` | TEXT | Ảnh đại diện topic (optional) |
| `post_count` | INTEGER | Số posts có topic này (denormalized) |
| `follower_count` | INTEGER | Số users follow topic này (denormalized) |
| `is_featured` | BOOLEAN | Topic nổi bật (admin feature) |
| `created_at`, `updated_at` | TIMESTAMP | Timestamps |

**Indexes:**
- `idx_topics_slug` - Tìm bằng slug
- `idx_topics_follower_count` - Sort topics theo popularity

**Note:** 
- Topics giống Threads - users có thể follow topics để xem posts về topic đó trong feed
- **Mỗi post chỉ có 1 topic** (one-to-many: topic → posts)
- **Post không có topic** → `topic_id IS NULL` trong bảng `posts`
- **CHỈ dùng cho Personal Posts** (`community_id IS NULL`). Community Posts **KHÔNG** dùng topics

#### `user_topic_follows`
Users follow topics để xem posts về topic đó trong feed.

| Column | Type | Description |
|--------|------|-------------|
| `user_id` | BIGINT (FK) | Reference to `users` |
| `topic_id` | BIGINT (FK) | Reference to `topics` |
| `created_at` | TIMESTAMP | Thời gian follow |

**Primary Key:** `(user_id, topic_id)`.

**Use Case:** 
- User follow topic "Technology" → Feed sẽ hiển thị posts có topic "Technology"
- Giống Threads: Follow topics để personalize feed

---

### 3.6. Interaction System

#### `post_likes`
Likes cho Posts. **Composite Primary Key** pattern cho junction table.

| Column | Type | Description |
|--------|------|-------------|
| `user_id` | BIGINT (FK, PK) | Reference to `users` |
| `post_id` | BIGINT (FK, PK) | Reference to `posts` |
| `created_at` | TIMESTAMP | Thời gian like |

**Primary Key:** `(user_id, post_id)` - Composite PK đảm bảo mỗi user chỉ like 1 post 1 lần.

**Indexes:**
- `idx_post_likes_post` - Query posts được like bởi ai
- `idx_post_likes_user` - Query posts user đã like
- `idx_post_likes_created` - Sort theo thời gian

#### `comment_likes`
Likes cho Comments. **Composite Primary Key** pattern cho junction table.

| Column | Type | Description |
|--------|------|-------------|
| `user_id` | BIGINT (FK, PK) | Reference to `users` |
| `comment_id` | BIGINT (FK, PK) | Reference to `comments` |
| `created_at` | TIMESTAMP | Thời gian like |

**Primary Key:** `(user_id, comment_id)` - Composite PK đảm bảo mỗi user chỉ like 1 comment 1 lần.

**Indexes:**
- `idx_comment_likes_comment` - Query comments được like bởi ai
- `idx_comment_likes_created` - Sort theo thời gian

#### `saved_posts`
Bookmark bài viết (trọng số cao: 8 điểm).

| Column | Type | Description |
|--------|------|-------------|
| `user_id` | BIGINT (FK) | Reference to `users` |
| `post_id` | BIGINT (FK) | Reference to `posts` |
| `saved_at` | TIMESTAMP | Thời gian lưu |

**Primary Key:** `(user_id, post_id)` - Mỗi user chỉ lưu 1 bài 1 lần.

#### `shares`
Chia sẻ bài viết (trọng số cao nhất: 10 điểm).

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | TSID |
| `user_id` | BIGINT (FK) | Reference to `users` |
| `post_id` | BIGINT (FK) | Reference to `posts` |
| `shared_at` | TIMESTAMP | Thời gian share |

#### `user_follows`
Quan hệ follow giữa users (hỗ trợ cả Public và Private accounts).

| Column | Type | Description |
|--------|------|-------------|
| `follower_id` | BIGINT (FK) | Reference to `users` (người follow) |
| `target_id` | BIGINT (FK) | Reference to `users` (người được follow) |
| `status` | ENUM | **PENDING**, ACCEPTED, REJECTED |
| `requested_at` | TIMESTAMP | Thời gian request follow |
| `accepted_at` | TIMESTAMP | Thời gian accept (nếu status = ACCEPTED) |
| `rejected_at` | TIMESTAMP | Thời gian reject (nếu status = REJECTED) |

**Primary Key:** `(follower_id, target_id)` - Composite PK.

**Indexes:**
- `idx_follows_follower` - Lấy danh sách đang follow (Build Feed)
- `idx_follows_target` - Lấy danh sách người theo dõi (Count/Notify)
- `idx_follows_status` - Filter theo status
- `idx_follows_target_pending` - Lấy follow requests PENDING của user

**Workflow:**
1. **Public Account:** Follow ngay → `status = ACCEPTED`, `accepted_at = now()`
2. **Private Account:** Follow request → `status = PENDING`
3. **Accept:** `status = ACCEPTED`, `accepted_at = now()`
4. **Reject:** `status = REJECTED`, `rejected_at = now()` (hoặc xóa record)

**Note:** 
- Dùng Internal TSID để join nhanh
- Giống Threads/Instagram: Private accounts cần approval
- Query feed chỉ lấy `status = ACCEPTED`

#### `join_requests`
Yêu cầu tham gia Community (chỉ cho Private Communities).

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | TSID |
| `user_id` | BIGINT (FK) | Reference to `users` (người xin join) |
| `community_id` | BIGINT (FK) | Reference to `communities` |
| `status` | ENUM | PENDING, APPROVED, REJECTED |
| `message` | TEXT | Lời nhắn khi xin join (optional) |
| `reviewed_by` | BIGINT (FK, NULL) | Reference to `users` (người duyệt) |
| `reviewed_at` | TIMESTAMP | Thời gian duyệt |
| `created_at`, `updated_at` | TIMESTAMP | Timestamps |

**Unique:** 
- `(user_id, community_id)` - Mỗi user chỉ xin vào 1 community 1 lần

**Indexes:**
- `idx_join_requests_community_status` - Lấy requests PENDING của community
- `idx_join_requests_user` - Lấy requests của user

**Workflow:**
1. User tạo request với `status = PENDING`
2. Admin/Moderator duyệt → `status = APPROVED` hoặc `REJECTED`
3. Khi APPROVED → Tự động thêm vào `community_members`

---

### 3.7. Media/Attachments

#### `media`
Ảnh/Video đính kèm cho Posts (Zero Compression cho Creator).

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | TSID |
| `post_id` | BIGINT (FK) | Reference to `posts` |
| `user_id` | BIGINT (FK) | Reference to `users` |
| `media_type` | VARCHAR(50) | IMAGE, VIDEO, GIF |
| `media_url` | TEXT | URL ảnh/video (Zero Compression) |
| `thumbnail_url` | TEXT | URL thumbnail |
| `file_size` | BIGINT | Kích thước file |
| `width`, `height` | INTEGER | Kích thước |
| `duration_seconds` | BIGINT | Duration (seconds) - cho video |
| `display_order` | INTEGER | Thứ tự hiển thị |
| `created_at` | TIMESTAMP | Timestamps |

**Indexes:**
- `idx_media_post` - Lấy media của post
- `idx_media_user` - Lấy media của user

**Note:** 
- **Zero Compression:** Không nén ảnh để giữ nguyên chất lượng tác phẩm (cho Creator).
- **Watermark:** Tích hợp watermark tự động để bảo vệ bản quyền.

---

### 3.8. Notifications

#### `notifications`
Thông báo cho users.

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | TSID |
| `user_id` | BIGINT (FK) | Reference to `users` |
| `type` | ENUM | LIKE, COMMENT, REPLY, FOLLOW, MENTION, COMMUNITY_INVITE, SYSTEM |
| `actor_id` | BIGINT (FK, NULL) | Reference to `users` (người thực hiện) |
| `post_id`, `comment_id`, `community_id` | BIGINT (FK, NULL) | Reference tùy context |
| `content` | TEXT | Nội dung thông báo |
| `is_read` | BOOLEAN | Đã đọc chưa |
| `created_at` | TIMESTAMP | Thời gian tạo |

**Indexes:**
- `idx_notifications_user_unread` - Lấy thông báo chưa đọc
- `idx_notifications_user_created` - Cursor-based pagination

---

## 4. QUAN HỆ GIỮA CÁC BẢNG (RELATIONSHIPS)

### 4.1. User-Centric
```
users (1) ──< (N) posts
users (1) ──< (N) comments
users (1) ──< (N) communities (owner)
users (1) ──< (N) series (creator)
users (N) ──< (N) user_follows (follower/target)
users (N) ──< (N) community_members
users (N) ──< (N) user_topic_follows ──< (N) topics
```

### 4.2. Community-First Architecture
```
communities (1) ──< (N) posts
communities (1) ──< (N) community_members
```

**Flow:**
- User join community → Nội dung từ community tự động xuất hiện trong Feed
- Community là "Nguồn cấp", không phải "phòng" để user phải vào xem

### 4.3. Posts & Series
```
series (1) ──< (N) posts
topics (1) ──< (N) posts (topic_id)  -- One-to-Many: Mỗi post chỉ có 1 topic
users (N) ──< (N) user_topic_follows ──< (N) topics
```

**Topics Flow:**
- **Mỗi post chỉ có 1 topic** (one-to-many: topic → posts)
- **Post không có topic** → `topic_id IS NULL`
- **CHỈ Personal Posts** (`community_id IS NULL`) mới có thể có topic
- Users follow topics → Feed hiển thị posts từ topics đã follow (giống Threads)

### 4.4. Comment System (Instagram-Style)
```
posts (1) ──< (N) comments
comments (1) ──< (N) comments (parent_comment_id)  -- Tối đa 1 cấp (Reply)
```

**Structure:**
- Comment chính: `parent_comment_id IS NULL`
- Reply: `parent_comment_id IS NOT NULL` (chỉ 1 cấp)

### 4.5. Interactions
```
posts (1) ──< (N) post_likes
posts (1) ──< (N) comments
posts (1) ──< (N) saved_posts
posts (1) ──< (N) shares
comments (1) ──< (N) comment_likes
```

---

## 5. INDEXING STRATEGY

### 5.1. Primary Indexes
- Hầu hết bảng dùng `BIGINT` PK (TSID) → B-Tree Index tự động
- Junction tables (likes, saves) dùng **Composite Primary Key** → Index tự động trên cả 2 columns

### 5.2. Foreign Key Indexes
- Tất cả FK đều có index để tối ưu JOIN

### 5.3. Search Indexes
- `public_id` (Short ID) → Unique Index cho tất cả entities
- `slug` → Index cho SEO (không unique cho communities)
- `display_name`, `email` → Index cho search users

### 5.4. Ranking Indexes
- `viral_score DESC` → Sắp xếp bài viết hot
- `created_at DESC` → Sắp xếp mới nhất

### 5.5. Cursor-Based Pagination Indexes
- `(post_id, created_at DESC, id DESC)` → Comments của post
- `(community_id, created_at DESC, id DESC)` → Posts của community
- `(author_id, created_at DESC, id DESC)` → Posts của user

### 5.6. Feed Building Indexes
- `(follower_id, status, created_at DESC)` → Following feed
- `(user_id, created_at DESC)` → Joined communities
- `(user_id, created_at DESC)` → Followed topics

---

## 6. CONSTRAINTS & VALIDATIONS

### 6.1. Unique Constraints
- `users.public_id` → Unique (NanoID)
- `users.email` → Unique
- `communities.public_id` → Unique (Short ID)
- `posts.public_id` → Unique (Short ID)
- `series.public_id` → Unique (Short ID)
- `topics.name` → Unique
- `topics.slug` → Unique
- `(user_id, post_id)` trong `post_likes` → Composite Primary Key
- `(user_id, comment_id)` trong `comment_likes` → Composite Primary Key
- `(user_id, post_id)` trong `saved_posts` → Unique
- `(follower_id, target_id)` trong `user_follows` → Unique
- `(user_id, topic_id)` trong `user_topic_follows` → Unique
- `(community_id, user_id)` trong `community_members` → Unique

### 6.2. Check Constraints
- `comments`: `parent_comment_id IS NULL OR parent_comment_id != id` (không reply chính mình)
- `user_follows`: `follower_id != target_id` (không follow chính mình)
- `posts`: `topic_id IS NULL OR community_id IS NULL` (chỉ Personal Posts mới có topic)

### 6.3. Foreign Key Constraints
- Tất cả FK đều có `ON DELETE CASCADE` hoặc `ON DELETE SET NULL` tùy logic nghiệp vụ

---

## 7. CURSOR-BASED PAGINATION

### 7.1. Feed Pagination
- **Cursor:** `(created_at, id)` - Dùng timestamp + ID để đảm bảo unique và stable
- **Query:** `WHERE (created_at, id) < (cursor_created_at, cursor_id) ORDER BY created_at DESC, id DESC LIMIT 20`

### 7.2. Comment Pagination
- **Cursor:** `(created_at, id)` - Tương tự Feed
- **Load Reply tại chỗ:** Khi bấm "Xem thêm", query `WHERE parent_comment_id = ? ORDER BY created_at ASC`

---

## 8. REDIS INTEGRATION

### 8.1. Tags/Mentions Management
- **Real-time Tags:** Lưu tags đang trending trong Redis ZSET
- **Mentions:** Lưu mentions (@username) để notify user ngay lập tức
- **Cache:** Cache kết quả search tags/mentions để giảm tải DB

### 8.2. Feed Pools (Gravity Algorithm)
- **Viral Pool:** Redis ZSET lưu `post_id` với `viral_score`
- **Following Pool:** Redis SET lưu `post_id` từ users đang follow
- **Community Pool:** Redis SET lưu `post_id` từ joined communities

---

## 9. MỞ RỘNG TƯƠNG LAI (FUTURE ENHANCEMENTS)

### 9.1. Partitioning
Khi đạt 1M+ users:
- `posts` → Range Partitioning theo `created_at` (theo tháng)
- `comments` → Range Partitioning theo `created_at` (theo tháng)

### 9.2. Read Replicas
- 1 Master (Write) + 2 Slaves (Read) cho PostgreSQL

### 9.3. Full-text Search
- Tích hợp Meilisearch cho full-text search phức tạp

---

## 10. LƯU Ý QUAN TRỌNG

1. **Không dùng UUID:** Dùng TSID để tối ưu B-Tree Index
2. **Public ID vs Internal ID:** Luôn dùng Internal ID để JOIN, chỉ dùng Public ID cho URL/API response
3. **Slug không unique:** Chỉ dùng cho SEO, query thực tế dùng `public_id`
4. **Stats denormalization:** Lưu stats trong `posts` để tránh COUNT() mỗi lần query
5. **Composite PK:** Dùng cho bảng Many-to-Many và bảng quan hệ (saved_posts, user_follows)
6. **Community-First:** Community là "Nguồn cấp", không phải "phòng" để user phải vào xem
7. **Comment Flat:** Tối đa 2 cấp (Comment chính và Reply), không nested sâu như Reddit
8. **Cursor-based Pagination:** Dùng cho Feed và Comment để không lag khi scroll

---

*End of Database Design Documentation.*
