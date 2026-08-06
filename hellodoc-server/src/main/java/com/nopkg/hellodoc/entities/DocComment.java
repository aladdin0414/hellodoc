package com.nopkg.hellodoc.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nopkg.hellodoc.enums.AnchorType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "kb_doc_comment")
@Data
public class DocComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id", nullable = false)
    private Long docId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private SysUser user;

    @Column(name = "parent_id")
    private Long parentId; // 回复的父评论

    @Enumerated(EnumType.STRING)
    private AnchorType anchorType;

    @Column(columnDefinition = "TEXT")
    private String anchorData; // JSON 格式的锚点数据

    @Column(columnDefinition = "TEXT")
    private String anchorText; // 被批注的原文

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "is_resolved")
    private Boolean isResolved = false;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
