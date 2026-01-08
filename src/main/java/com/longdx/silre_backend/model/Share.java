package com.longdx.silre_backend.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import jakarta.persistence.*;
import com.longdx.silre_backend.config.TsidGenerator;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;

@Entity
@Table(name = "shares")
@Data
public class Share {

    @Id
    @TsidGenerator
    @Column(name = "id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    @NotNull
    private Post post;

    @Column(name = "shared_at", nullable = false, updatable = false)
    private OffsetDateTime sharedAt;

    @PrePersist
    protected void onCreate() {
        sharedAt = OffsetDateTime.now();
    }
}

