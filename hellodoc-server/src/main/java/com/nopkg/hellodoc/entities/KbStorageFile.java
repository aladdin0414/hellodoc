package com.nopkg.hellodoc.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "kb_storage_file")
public class KbStorageFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "storage_config_id", nullable = false)
    private KbStorageConfig storageConfig;

    @NotNull
    @Column(name = "storage_key", nullable = false, length = Integer.MAX_VALUE)
    private String storageKey;

    @Size(max = 64)
    @NotNull
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Size(max = 255)
    @Column(name = "file_type", length = 255)
    private String fileType;

    @NotNull
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @ColumnDefault("1")
    @Column(name = "ref_count")
    private Integer refCount;

    @Column(name = "access_url", length = Integer.MAX_VALUE)
    private String accessUrl;

    @Column(name = "url_expires_at")
    private OffsetDateTime urlExpiresAt;

    @ColumnDefault("now()")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

}