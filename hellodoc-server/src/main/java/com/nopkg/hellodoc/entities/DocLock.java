package com.nopkg.hellodoc.entities;

import com.nopkg.hellodoc.enums.LockType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "kb_doc_lock")
@Data
public class DocLock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long docId;
    private Long userId;
    private String sessionId;

    private LockType lockType;

    private String blockId; // 用于块级锁
    private Integer rangeStart; // 用于范围级锁
    private Integer rangeEnd;

    private LocalDateTime acquiredAt;
    private LocalDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        if (acquiredAt == null) {
            acquiredAt = LocalDateTime.now();
        }
    }
}
