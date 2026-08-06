package com.nopkg.hellodoc.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "kb_doc_session")
@Data
public class DocSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long docId;
    private Long userId;
    private String sessionId;

    @Column(columnDefinition = "TEXT")
    private String cursorPosition; // JSON 格式的光标位置

    @Column(columnDefinition = "TEXT")
    private String selectionRange; // JSON 格式的选择范围

    private String userColor;
    private Boolean isActive;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime joinedAt;
}
