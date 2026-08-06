package com.nopkg.hellodoc.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "kb_storage_config")
public class KbStorageConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 64)
    @NotNull
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Size(max = 32)
    @NotNull
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Size(max = 128)
    @Column(name = "bucket", length = 128)
    private String bucket;

    @Size(max = 32)
    @Column(name = "region", length = 32)
    private String region;

    @Column(name = "endpoint", length = Integer.MAX_VALUE)
    private String endpoint;

    @Size(max = 128)
    @Column(name = "access_key_id", length = 128)
    private String accessKeyId;

    @Column(name = "secret_key_encrypted", length = Integer.MAX_VALUE)
    private String secretKeyEncrypted;

    @Column(name = "cdn_domain", length = Integer.MAX_VALUE)
    private String cdnDomain;

    @ColumnDefault("false")
    @Column(name = "is_default")
    private Boolean isDefault;

    @ColumnDefault("true")
    @Column(name = "is_active")
    private Boolean isActive;

    @ColumnDefault("now()")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("now()")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}