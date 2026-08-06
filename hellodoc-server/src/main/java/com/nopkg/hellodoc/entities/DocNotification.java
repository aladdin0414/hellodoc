package com.nopkg.hellodoc.entities;

import com.nopkg.hellodoc.enums.NotifyType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "kb_doc_notification")
@Data
public class DocNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long docId;

    @Column(nullable = false)
    private Long userId; // 接收者

    @Column(nullable = false)
    private Long senderId; // 发送者

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotifyType notifyType;

    private Long refId; // 关联的评论/操作ID

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "title_key", length = 128)
    private String titleKey;

    @Column(name = "content_key", length = 128)
    private String contentKey;

    @Column(name = "template_params", columnDefinition = "TEXT")
    private String templateParams;

    @Column(name = "channel", length = 16)
    private String channel;

    private Boolean isRead = false;

    private LocalDateTime readAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
