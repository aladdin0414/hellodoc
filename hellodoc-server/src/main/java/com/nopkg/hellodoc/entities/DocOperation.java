package com.nopkg.hellodoc.entities;

import com.nopkg.hellodoc.enums.OpType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "kb_doc_operation")
@Data
public class DocOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long docId;
    private Long userId;
    private String sessionId;

    @Enumerated(EnumType.STRING)
    private OpType opType;

    @Column(columnDefinition = "TEXT")
    private String opData; // 操作的 JSON 内容

    private Integer baseVersion; // 客户端的基础版本
    private Integer serverVersion; // 服务器分配的版本

    @Column(columnDefinition = "TEXT")
    private String transformedData; // OT 转换后的数据

    private String batchId;
    private Boolean isMerged;
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
