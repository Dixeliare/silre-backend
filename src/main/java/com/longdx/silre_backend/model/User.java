package com.longdx.silre_backend.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.*;
import com.longdx.silre_backend.config.TsidGenerator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import com.longdx.silre_backend.config.TimezoneContext;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @TsidGenerator
    @Column(name = "internal_id")
    @JsonSerialize(using = ToStringSerializer.class) // Convert Long -> String cho JSON (tránh mất độ chính xác)
    private Long internalId;

    @Column(name = "public_id", unique = true, nullable = false, length = 20)
    @NotBlank
    @Size(max = 20)
    private String publicId; // NanoID Suffix (e.g., "Xy9zQ2mP")

    @Column(name = "display_name", nullable = false)
    @NotBlank
    @Size(max = 255)
    private String displayName; // Tên hiển thị (có thể chứa Emoji, CJK)

    @Column(name = "email", unique = true, nullable = false)
    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    @NotBlank
    @Size(max = 255)
    private String passwordHash; // Bcrypt hash

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "avatar_url", length = 255)
    @Size(max = 255)
    private String avatarUrl;

    @Column(name = "banner_id", length = 255)
    @Size(max = 255)
    private String bannerId;

    @Column(name = "settings_display_sensitive_media", nullable = false)
    private Boolean settingsDisplaySensitiveMedia = false; // NSFW setting

    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate = false; // Private account (cần approval khi follow)

    @Column(name = "account_status", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String accountStatus = "ACTIVE"; // ACTIVE, SUSPENDED, DELETED

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt; // Soft delete

    @Column(name = "deletion_reason", length = 255)
    @Size(max = 255)
    private String deletionReason;

    @Column(name = "timezone", length = 255)
    @Size(max = 255)
    private String timezone = "UTC";

    @Column(name = "last_public_id_changed_at")
    private OffsetDateTime lastPublicIdChangedAt; // Khi nào public_id thay đổi

    @Column(name = "is_searchable_by_public_id", nullable = false)
    private Boolean isSearchableByPublicId = true; // Có thể search bằng public_id

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @PrePersist
    protected void onCreate() {
        // Use detected timezone from request, or user's stored timezone, or UTC
        ZoneId zoneId = getEffectiveTimezone();
        createdAt = OffsetDateTime.now(zoneId);
    }

    @PreUpdate
    protected void onUpdate() {
        // Use detected timezone from request, or user's stored timezone, or UTC
        ZoneId zoneId = getEffectiveTimezone();
        updatedAt = OffsetDateTime.now(zoneId);
    }
    
    /**
     * Gets the effective timezone for this user.
     * Priority:
     * 1. Timezone from request context (X-Timezone header)
     * 2. User's stored timezone preference
     * 3. Default: UTC
     * 
     * @return ZoneId to use for timestamp creation
     */
    private ZoneId getEffectiveTimezone() {
        // Priority 1: Check if timezone was detected from request header
        ZoneId contextTimezone = TimezoneContext.getCurrentTimezone();
        if (contextTimezone != null && !contextTimezone.equals(ZoneId.of("UTC"))) {
            return contextTimezone;
        }
        
        // Priority 2: Use user's stored timezone preference
        if (timezone != null && !timezone.trim().isEmpty() && !timezone.equals("UTC")) {
            try {
                return ZoneId.of(timezone);
            } catch (Exception e) {
                // Invalid timezone stored - fallback to UTC
                return ZoneId.of("UTC");
            }
        }
        
        // Priority 3: Default to UTC
        return ZoneId.of("UTC");
    }
}

