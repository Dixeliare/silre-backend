-- =====================================================
-- INITIAL DATABASE SCHEMA
-- Silre Social Platform Backend
-- Version: 2.0 (Community-First Architecture)
-- Author: LongDx
-- =====================================================
-- Note: Tất cả business logic được xử lý ở Backend
-- Database chỉ lưu schema thuần, không có triggers, functions, hoặc CHECK constraints
-- =====================================================
-- Community-First Architecture: Community là "Nguồn cấp", không phải "phòng" để user phải vào xem
-- Loại bỏ Forum hoàn toàn để tránh làm rối UI và UX
-- =====================================================

-- =====================================================
-- 1. USER IDENTITY & AUTHENTICATION
-- =====================================================

CREATE TABLE users (
    internal_id BIGINT PRIMARY KEY,                    -- TSID (Time-Sorted ID)
    public_id VARCHAR(20) NOT NULL UNIQUE,             -- NanoID Suffix (e.g., "Xy9zQ2mP")
    display_name VARCHAR(255) NOT NULL,                -- Tên hiển thị (có thể chứa Emoji, CJK)
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,               -- Bcrypt hash
    bio TEXT,                                          -- Bio của user
    avatar_url VARCHAR(255),                           -- Avatar URL
    banner_id VARCHAR(255),                            -- Banner ID
    settings_display_sensitive_media BOOLEAN DEFAULT FALSE, -- NSFW setting (display_nsfw)
    is_private BOOLEAN DEFAULT FALSE,                  -- Private account (cần approval khi follow)
    account_status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, DELETED
    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,               -- Soft delete
    deletion_reason VARCHAR(255),                      -- Lý do xóa
    timezone VARCHAR(255) DEFAULT 'UTC',              -- Timezone
    last_public_id_changed_at TIMESTAMP WITH TIME ZONE, -- Khi nào public_id thay đổi
    is_searchable_by_public_id BOOLEAN DEFAULT TRUE,   -- Có thể search bằng public_id
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_users_public_id ON users(public_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_display_name ON users(display_name);
CREATE INDEX idx_users_account_status ON users(account_status);
CREATE INDEX idx_users_is_active ON users(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE users IS 'Bảng người dùng với Dual-Key Identity (TSID internal + NanoID public)';


-- =====================================================
-- 2. SOCIAL SYSTEM (Communities & Posts)
-- =====================================================

CREATE TABLE communities (
    id BIGINT PRIMARY KEY,                              -- TSID (internal_id)
    name VARCHAR(255) NOT NULL,                         -- Display name (display_name)
    slug VARCHAR(255) NOT NULL,                         -- Không unique (có thể trùng)
    public_id VARCHAR(10) NOT NULL UNIQUE,              -- Short ID cho URL (slug.public_id) - không phải BIGINT
    description TEXT,
    owner_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE RESTRICT,
    avatar_url VARCHAR(255),                             -- Avatar URL
    cover_url VARCHAR(255),                             -- Cover URL
    is_nsfw BOOLEAN DEFAULT FALSE,                      -- NSFW flag
    is_private BOOLEAN DEFAULT FALSE,                   -- Private community
    is_searchable BOOLEAN DEFAULT TRUE,                 -- Có thể search
    member_count INTEGER DEFAULT 0,
    post_count INTEGER DEFAULT 0,
    updated_user_id BIGINT REFERENCES users(internal_id) ON DELETE SET NULL, -- User cập nhật gần nhất
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_communities_public_id ON communities(public_id);
CREATE INDEX idx_communities_owner ON communities(owner_id);
CREATE INDEX idx_communities_slug ON communities(slug);
CREATE INDEX idx_communities_is_searchable ON communities(is_searchable) WHERE is_searchable = TRUE;

COMMENT ON TABLE communities IS 'Nhóm cộng đồng - Community là "Nguồn cấp", không phải "phòng" để user phải vào xem. community_id NULL trong posts = Personal Post';

-- =====================================================
-- TOPICS SYSTEM (Phải tạo trước posts vì posts có FK đến topics)
-- =====================================================

CREATE TABLE topics (
    id BIGINT PRIMARY KEY,                              -- TSID (internal_id)
    name VARCHAR(255) NOT NULL UNIQUE,                  -- Tên topic (e.g., "Technology", "Hà Nội")
    slug VARCHAR(255) NOT NULL UNIQUE,                 -- URL slug (e.g., "technology", "hanoi")
    description TEXT,                                   -- Mô tả topic
    image_url TEXT,                                      -- Ảnh đại diện topic (optional)
    post_count INTEGER DEFAULT 0,                       -- Số posts có topic này (denormalized)
    follower_count INTEGER DEFAULT 0,                   -- Số users follow topic này (denormalized)
    is_featured BOOLEAN DEFAULT FALSE,                 -- Topic nổi bật (admin feature)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_topics_slug ON topics(slug);
CREATE INDEX idx_topics_name ON topics(name);
CREATE INDEX idx_topics_featured ON topics(is_featured) WHERE is_featured = TRUE;
CREATE INDEX idx_topics_follower_count ON topics(follower_count DESC); -- Sort topics theo popularity

COMMENT ON TABLE topics IS 'Topics system giống Threads (Meta). Users có thể follow topics để xem posts về topic đó. Mỗi post chỉ có 1 topic (one-to-many: topic → posts). Post có thể không có topic (topic_id IS NULL trong posts). CHỈ dùng cho Personal Posts';

-- =====================================================
-- SERIES SYSTEM (Cho Creator - Gom bài đăng thành tập/chapter)
-- =====================================================

CREATE TABLE series (
    id BIGINT PRIMARY KEY,                              -- TSID
    creator_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,                        -- Tên series
    description TEXT,                                   -- Mô tả series
    public_id VARCHAR(12) NOT NULL UNIQUE,             -- Short ID cho URL
    slug VARCHAR(350),                                 -- Slug (SEO)
    post_count INTEGER DEFAULT 0,                       -- Số bài trong series (denormalized)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_series_creator ON series(creator_id);
CREATE INDEX idx_series_public_id ON series(public_id);
CREATE INDEX idx_series_slug ON series(slug);

COMMENT ON TABLE series IS 'Series cho Creator - Gom các bài đăng thành tập/chapter. User có thể lướt xem trọn bộ bằng viewer chuyên dụng';

CREATE TABLE posts (
    id BIGINT PRIMARY KEY,                              -- TSID
    author_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    community_id BIGINT REFERENCES communities(id) ON DELETE SET NULL, -- NULL = Personal Post
    series_id BIGINT REFERENCES series(id) ON DELETE SET NULL,         -- Cho Creator - gom bài thành tập/chapter
    topic_id BIGINT REFERENCES topics(id) ON DELETE SET NULL,          -- CHỈ DÙNG CHO PERSONAL POSTS. NULL = post không có topic
    title VARCHAR(255),                                 -- Title (optional, có thể NULL cho social posts)
    content TEXT NOT NULL,
    public_id VARCHAR(12) NOT NULL UNIQUE,             -- Short ID cho URL - không phải BIGINT
    slug VARCHAR(350),                                 -- Slug (auto-generated từ title/content, SEO)
    is_nsfw BOOLEAN DEFAULT FALSE,                      -- NSFW flag (kế thừa từ community nếu có)
    
    -- Stats cho Ranking Algorithm (Gravity Score)
    likes_count INTEGER DEFAULT 0,
    comments_count INTEGER DEFAULT 0,
    shares_count INTEGER DEFAULT 0,
    saves_count INTEGER DEFAULT 0,
    tags_count INTEGER DEFAULT 0,                       -- Số lượt tag bạn bè trong comment
    caption_expands_count INTEGER DEFAULT 0,            -- Số lượt bấm "Xem thêm"
    media_clicks_count INTEGER DEFAULT 0,              -- Số lượt click vào ảnh/video (media_clicks)
    dwell_7s_count INTEGER DEFAULT 0,                  -- Số lượt ở lại > 7 giây
    viral_score DECIMAL(20, 10) DEFAULT 0,            -- Điểm tính từ Gravity Algorithm
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
    -- Note: Business logic (topic chỉ cho Personal Posts) được xử lý ở Backend
);

CREATE INDEX idx_posts_author ON posts(author_id);
CREATE INDEX idx_posts_community ON posts(community_id);
CREATE INDEX idx_posts_series ON posts(series_id) WHERE series_id IS NOT NULL;
CREATE INDEX idx_posts_topic ON posts(topic_id) WHERE topic_id IS NOT NULL; -- Index cho posts có topic
CREATE INDEX idx_posts_public_id ON posts(public_id);
CREATE INDEX idx_posts_created ON posts(created_at DESC);
CREATE INDEX idx_posts_viral_score ON posts(viral_score DESC);
CREATE INDEX idx_posts_community_created ON posts(community_id, created_at DESC) WHERE community_id IS NOT NULL;
CREATE INDEX idx_posts_topic_created ON posts(topic_id, created_at DESC) WHERE topic_id IS NOT NULL; -- Lấy posts của topic (cho feed)
CREATE INDEX idx_posts_series_created ON posts(series_id, created_at ASC) WHERE series_id IS NOT NULL; -- Lấy posts trong series theo thứ tự

COMMENT ON TABLE posts IS 'Bài viết Social. community_id NULL = Personal Post, NOT NULL = Community Post. topic_id chỉ dùng cho Personal Posts (có thể NULL nếu post không có topic). series_id cho Creator để gom bài thành tập/chapter';


-- =====================================================
-- 3. INTERACTION SYSTEM (Likes, Comments, Saves, Shares, Follows)
-- =====================================================

-- Comments Table (Instagram-Style - Flat, tối đa 2 cấp)
CREATE TABLE comments (
    id BIGINT PRIMARY KEY,                              -- TSID
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    parent_comment_id BIGINT REFERENCES comments(id) ON DELETE CASCADE, -- NULL = Comment chính, NOT NULL = Reply (chỉ 1 cấp)
    content TEXT NOT NULL,
    likes_count INTEGER DEFAULT 0,                      -- Denormalized count (từ comment_likes)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_comments_post ON comments(post_id);
CREATE INDEX idx_comments_author ON comments(author_id);
CREATE INDEX idx_comments_parent ON comments(parent_comment_id) WHERE parent_comment_id IS NOT NULL;
CREATE INDEX idx_comments_post_created ON comments(post_id, created_at DESC, id DESC); -- Cursor-based pagination
CREATE INDEX idx_comments_parent_created ON comments(parent_comment_id, created_at ASC, id ASC); -- Load replies tại chỗ

COMMENT ON TABLE comments IS 'Comments cho Posts (Instagram-Style - Flat). Tối đa 2 cấp: Comment chính (parent_comment_id IS NULL) và Reply (parent_comment_id IS NOT NULL, chỉ 1 cấp)';

-- Separate Likes Tables (Best Practice: Tách riêng để có FK constraint và optimize tốt hơn)
CREATE TABLE post_likes (
    user_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, post_id)  -- Composite PK: Mỗi user chỉ like 1 post 1 lần
);

CREATE INDEX idx_post_likes_post ON post_likes(post_id);  -- Query posts được like bởi ai
CREATE INDEX idx_post_likes_user ON post_likes(user_id);  -- Query posts user đã like
CREATE INDEX idx_post_likes_created ON post_likes(created_at DESC);  -- Sort theo thời gian

COMMENT ON TABLE post_likes IS 'Likes cho Posts (Personal + Community). Composite PK (user_id, post_id) - Junction table pattern';

CREATE TABLE comment_likes (
    user_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    comment_id BIGINT NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, comment_id)  -- Composite PK: Mỗi user chỉ like 1 comment 1 lần
);

CREATE INDEX idx_comment_likes_comment ON comment_likes(comment_id);  -- Query comments được like bởi ai
CREATE INDEX idx_comment_likes_created ON comment_likes(created_at DESC);  -- Sort theo thời gian

COMMENT ON TABLE comment_likes IS 'Likes cho Comments. Composite PK (user_id, comment_id) - Junction table pattern';

CREATE TABLE saved_posts (
    user_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    saved_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, post_id)                     -- Composite PK: Mỗi user chỉ lưu 1 bài 1 lần
);

CREATE INDEX idx_saved_posts_user ON saved_posts(user_id);
CREATE INDEX idx_saved_posts_post ON saved_posts(post_id);

COMMENT ON TABLE saved_posts IS 'Bookmark posts. Trọng số cao (8 điểm) trong ranking algorithm';

CREATE TABLE shares (
    id BIGINT PRIMARY KEY,                              -- TSID
    user_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    shared_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shares_user ON shares(user_id);
CREATE INDEX idx_shares_post ON shares(post_id);
CREATE INDEX idx_shares_created ON shares(shared_at DESC);

COMMENT ON TABLE shares IS 'Share posts. Trọng số cao nhất (10 điểm) trong ranking algorithm';

-- Follow Requests/Relationships: Hỗ trợ cả Public (follow ngay) và Private (cần approval)
CREATE TYPE follow_status AS ENUM ('PENDING', 'ACCEPTED', 'REJECTED');

CREATE TABLE user_follows (
    follower_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    target_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    status follow_status DEFAULT 'ACCEPTED',            -- PENDING nếu target là private account
    requested_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời gian request
    accepted_at TIMESTAMP WITH TIME ZONE,               -- Thời gian accept (nếu status = ACCEPTED)
    rejected_at TIMESTAMP WITH TIME ZONE,               -- Thời gian reject (nếu status = REJECTED)
    PRIMARY KEY (follower_id, target_id)              -- Composite PK
    -- Note: Business logic (không follow chính mình) được xử lý ở Backend
);

CREATE INDEX idx_follows_follower ON user_follows(follower_id);  -- Lấy danh sách đang follow (Build Feed)
CREATE INDEX idx_follows_target ON user_follows(target_id);      -- Lấy danh sách người theo dõi (Count/Notify)
CREATE INDEX idx_follows_status ON user_follows(status);         -- Filter theo status
CREATE INDEX idx_follows_target_pending ON user_follows(target_id, status) WHERE status = 'PENDING'; -- Lấy follow requests PENDING của user
CREATE INDEX idx_follows_follower_accepted ON user_follows(follower_id, requested_at DESC) WHERE status = 'ACCEPTED'; -- Cursor-based pagination cho feed

COMMENT ON TABLE user_follows IS 'Follow relationships với status. Public accounts: status=ACCEPTED ngay. Private accounts: status=PENDING, cần approval';

-- Join Requests: User xin vào Community (chỉ cho Private Communities)
CREATE TYPE join_request_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');

CREATE TABLE join_requests (
    id BIGINT PRIMARY KEY,                              -- TSID
    user_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    community_id BIGINT NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    status join_request_status DEFAULT 'PENDING',
    message TEXT,                                        -- Lời nhắn khi xin join (optional)
    reviewed_by BIGINT REFERENCES users(internal_id) ON DELETE SET NULL, -- Người duyệt (admin/moderator)
    reviewed_at TIMESTAMP WITH TIME ZONE,               -- Thời gian duyệt
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_join_requests_user ON join_requests(user_id);
CREATE INDEX idx_join_requests_community ON join_requests(community_id);
CREATE INDEX idx_join_requests_status ON join_requests(status);
CREATE INDEX idx_join_requests_community_status ON join_requests(community_id, status) WHERE status = 'PENDING';
CREATE INDEX idx_join_requests_created ON join_requests(created_at DESC);

CREATE UNIQUE INDEX uq_join_request_user_community ON join_requests(user_id, community_id);

COMMENT ON TABLE join_requests IS 'Yêu cầu tham gia Community (chỉ cho Private Communities). Status: PENDING, APPROVED, REJECTED';

-- User Topic Follows: Users follow topics để xem posts về topic đó trong feed
CREATE TABLE user_topic_follows (
    user_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    topic_id BIGINT NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, topic_id)
);

CREATE INDEX idx_user_topic_follows_user ON user_topic_follows(user_id);
CREATE INDEX idx_user_topic_follows_topic ON user_topic_follows(topic_id);

COMMENT ON TABLE user_topic_follows IS 'Users follow topics. Dùng để build feed: lấy posts từ topics user đã follow';


-- =====================================================
-- 4. COMMUNITY MEMBERSHIP
-- =====================================================

CREATE TABLE community_members (
    community_id BIGINT NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    role VARCHAR(50) DEFAULT 'MEMBER',                 -- MEMBER, MODERATOR, ADMIN (role_id -> role)
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(255) DEFAULT 'ACTIVE',               -- ACTIVE, BANNED, LEFT
    PRIMARY KEY (community_id, user_id)
);

CREATE INDEX idx_community_members_community ON community_members(community_id);
CREATE INDEX idx_community_members_user ON community_members(user_id);
CREATE INDEX idx_community_members_role ON community_members(role);
CREATE INDEX idx_community_members_status ON community_members(status);
CREATE INDEX idx_community_members_user_created ON community_members(user_id, joined_at DESC); -- Cursor-based pagination

COMMENT ON TABLE community_members IS 'Membership trong Communities. Role: MEMBER, MODERATOR, ADMIN. User join community để nội dung tự động xuất hiện trong Feed';


-- =====================================================
-- 5. MEDIA/ATTACHMENTS (Zero Compression cho Creator)
-- =====================================================

CREATE TABLE media (
    id BIGINT PRIMARY KEY,                              -- TSID
    post_id BIGINT REFERENCES posts(id) ON DELETE CASCADE,
    comment_id BIGINT REFERENCES comments(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    media_type VARCHAR(50) NOT NULL,                   -- IMAGE, VIDEO, GIF (type)
    media_url TEXT NOT NULL,                            -- Media URL (Zero Compression cho Creator)
    thumbnail_url TEXT,                                 -- Thumbnail URL
    file_size BIGINT,                                   -- File size (bytes)
    width INTEGER,                                      -- Width
    height INTEGER,                                     -- Height
    duration_seconds BIGINT,                           -- Duration (seconds) - cho video
    position INTEGER DEFAULT 0,                         -- Display order (position)
    display_order INTEGER DEFAULT 0,                    -- Display order
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    -- Note: Business logic (media phải có post hoặc comment) được xử lý ở Backend
);

CREATE INDEX idx_media_post ON media(post_id) WHERE post_id IS NOT NULL;
CREATE INDEX idx_media_comment ON media(comment_id) WHERE comment_id IS NOT NULL;
CREATE INDEX idx_media_user ON media(user_id);
CREATE INDEX idx_media_post_order ON media(post_id, display_order ASC) WHERE post_id IS NOT NULL; -- Sort media trong post

COMMENT ON TABLE media IS 'Media attachments cho Posts và Comments. Zero Compression để giữ nguyên chất lượng tác phẩm (cho Creator). Watermark tự động được tích hợp';


-- =====================================================
-- 6. NOTIFICATIONS
-- =====================================================

CREATE TYPE notification_type AS ENUM (
    'LIKE', 'COMMENT', 'REPLY', 'FOLLOW', 
    'MENTION', 'COMMUNITY_INVITE', 'SYSTEM'
);

CREATE TABLE notifications (
    id BIGINT PRIMARY KEY,                              -- TSID
    user_id BIGINT NOT NULL REFERENCES users(internal_id) ON DELETE CASCADE,
    type notification_type NOT NULL,
    actor_id BIGINT REFERENCES users(internal_id) ON DELETE SET NULL,
    post_id BIGINT REFERENCES posts(id) ON DELETE CASCADE,
    comment_id BIGINT REFERENCES comments(id) ON DELETE CASCADE,
    community_id BIGINT REFERENCES communities(id) ON DELETE CASCADE,
    content TEXT,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read, created_at DESC) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_created ON notifications(created_at DESC);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC, id DESC); -- Cursor-based pagination

COMMENT ON TABLE notifications IS 'Thông báo cho users';


-- =====================================================
-- 7. INITIAL DATA (Featured Topics)
-- =====================================================

-- Insert một số Topics mặc định (giống Threads của Meta)
INSERT INTO topics (id, name, slug, description, is_featured) VALUES
    (1000000000000000001, 'Technology', 'technology', 'Công nghệ và phần mềm', TRUE),
    (1000000000000000002, 'News', 'news', 'Tin tức và thời sự', TRUE),
    (1000000000000000003, 'Entertainment', 'entertainment', 'Giải trí và văn hóa', TRUE),
    (1000000000000000004, 'Sports', 'sports', 'Thể thao', TRUE),
    (1000000000000000005, 'Lifestyle', 'lifestyle', 'Lối sống', TRUE),
    (1000000000000000006, 'Education', 'education', 'Giáo dục và học tập', TRUE)
ON CONFLICT DO NOTHING;
