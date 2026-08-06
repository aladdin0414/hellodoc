package com.nopkg.hellodoc.entities;

import com.nopkg.hellodoc.enums.DocStatus;
import com.nopkg.hellodoc.enums.DocType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "kb_document")
public class KbDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "kb_id", nullable = false)
    private KbKnowledgeBase kb;

    @Size(max = 255)
    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull
    @Column(name = "type", nullable = false, length = 16)
    private DocType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "parent_id")
    private KbDocument parent;

    @Size(max = 255)
    @Column(name = "slug")
    private String slug;

    @Column(name = "path", columnDefinition = "ltree")
    @ColumnTransformer(write = "?::ltree")
    private String path;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "order_num", nullable = false)
    private Integer orderNum;

    @ColumnDefault("'draft'")
    @Column(name = "status", length = 16)
    private DocStatus status;

    @Column(name = "summary", length = Integer.MAX_VALUE)
    private String summary;

    @Size(max = 32)
    @Column(name = "paper_bg_color", length = 32)
    private String paperBgColor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_meta")
    private Map<String, Object> extraMeta;

    @Column(name = "paper_bg_image", length = Integer.MAX_VALUE)
    private String paperBgImage;

    @ColumnDefault("false")
    @Column(name = "is_cover")
    private Boolean isCover;

    @ColumnDefault("false")
    @Column(name = "is_open")
    private Boolean isOpen;

    @ColumnDefault("false")
    @Column(name = "is_encrypted")
    private Boolean isEncrypted;

    @Size(max = 32)
    @Column(name = "enc_algorithm", length = 32)
    private String encAlgorithm;

    @Size(max = 128)
    @Column(name = "enc_salt", length = 128)
    private String encSalt;

    @Size(max = 255)
    @Column(name = "password")
    private String password;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enc_meta")
    private Map<String, Object> encMeta;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "last_editor_id")
    private Long lastEditorId;

    @ColumnDefault("0")
    @Column(name = "word_count")
    private Integer wordCount;

    @ColumnDefault("0")
    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "file_size")
    private Long fileSize;

    @Size(max = 128)
    @Column(name = "file_id", length = 128)
    private String fileId;

    @Column(name = "file_storage_key", length = Integer.MAX_VALUE)
    private String fileStorageKey;

    @ColumnDefault("0")
    @Column(name = "current_version")
    private Integer currentVersion;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Size(max = 64)
    @Column(name = "enc_key_id", length = 64)
    private String encKeyId;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @ColumnDefault("now()")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

}
