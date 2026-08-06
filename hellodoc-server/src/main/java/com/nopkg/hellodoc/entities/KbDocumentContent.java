package com.nopkg.hellodoc.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "kb_document_content")
public class KbDocumentContent {
    @Id
    @Column(name = "doc_id")
    private Long docId;

    @Lob
    @Column(name = "content", length = Integer.MAX_VALUE)
    private String content;

    @ColumnDefault("now()")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
