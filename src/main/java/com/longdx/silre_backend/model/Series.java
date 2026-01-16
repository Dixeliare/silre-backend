package com.longdx.silre_backend.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.*;
import com.longdx.silre_backend.config.TsidGenerator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Series Entity
 * 
 * Cho phép Creator gom các bài đăng lẻ thành một tập/chapter.
 * User có thể lướt xem trọn bộ bằng viewer chuyên dụng thay vì xem từng ảnh rời rạc.
 */
@Entity
@Table(name = "series")
@Data
public class Series {

    @Id
    @TsidGenerator
    @Column(name = "id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    @NotNull
    private User creator; // Creator của series

    @Column(name = "title", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String title; // Tên series

    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // Mô tả series

    @Column(name = "public_id", unique = true, nullable = false, length = 12)
    @NotBlank
    @Size(max = 12)
    private String publicId; // Short ID cho URL

    @Column(name = "slug", length = 350)
    @Size(max = 350)
    private String slug; // Slug (SEO)

    @Column(name = "post_count", nullable = false)
    private Integer postCount = 0; // Số bài trong series (denormalized)

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
