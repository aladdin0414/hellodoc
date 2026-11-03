package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbDocument;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Table(name = "mcp_document_update_operation", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "doc_id", "request_id"}))
@Getter @Setter
public class McpDocumentUpdateOperation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id", nullable = false)
    private KbDocument document;
    @Column(name = "request_id", nullable = false, length = 128) private String requestId;
    @Column(name = "input_hash", nullable = false, length = 64) private String inputHash;
    @Column(name = "result_version", nullable = false) private Integer resultVersion;
    @Column(name = "result_updated_at", nullable = false) private OffsetDateTime resultUpdatedAt;
}
