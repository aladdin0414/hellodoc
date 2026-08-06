package com.nopkg.hellodoc.entities;

import com.nopkg.hellodoc.enums.PublicRole;
import com.nopkg.hellodoc.enums.Visibility;
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
@Table(name = "kb_knowledge_base")
public class KbKnowledgeBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 255)
    @NotNull
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @Size(max = 64)
    @Column(name = "icon", length = 64)
    private String icon;

    @Size(max = 32)
    @Column(name = "color", length = 32)
    private String color;

    @Column(name = "owner_id")
    private Long ownerId;

    @ColumnDefault("false")
    @Column(name = "allow_anonymous")
    private Boolean allowAnonymous;

    @ColumnDefault("'private'")
    @Column(name = "visibility", length = 16)
    private Visibility visibility;

    @ColumnDefault("'viewer'")
    @Column(name = "public_role", length = 16)
    private PublicRole publicRole;

    @Column(name = "cover_image", length = Integer.MAX_VALUE)
    private String coverImage;

    @ColumnDefault("now()")
    @Column(name = "created_at")
    private java.time.OffsetDateTime createdAt;

    @ColumnDefault("now()")
    @Column(name = "updated_at")
    private java.time.OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private java.time.OffsetDateTime deletedAt;

}