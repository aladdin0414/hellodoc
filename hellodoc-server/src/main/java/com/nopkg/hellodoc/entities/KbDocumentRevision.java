package com.nopkg.hellodoc.entities;

import com.nopkg.hellodoc.enums.RevisionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "kb_document_revision")
public class KbDocumentRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "doc_id", nullable = false)
    private KbDocument doc;

    @NotNull
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "content", length = Integer.MAX_VALUE)
    private String content;

    @Column(name = "diff_content", length = Integer.MAX_VALUE)
    private String diffContent;

    @Column(name = "author_user_id")
    private Long authorUserId;

    @Size(max = 255)
    @Column(name = "message")
    private String message;

    @Column(name = "word_count")
    private Integer wordCount;

    @ColumnDefault("'manual'")
    @Column(name = "revision_type", length = 20)
    private RevisionType revisionType;

    @ColumnDefault("false")
    @Column(name = "is_encrypted")
    private Boolean isEncrypted;

    @ColumnDefault("now()")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "archive_storage_key", length = 1000)
    private String archiveStorageKey;

}