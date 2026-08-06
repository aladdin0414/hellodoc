package com.nopkg.hellodoc.entities;

import jakarta.persistence.*;
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
@Table(name = "kb_doc_relation")
public class KbDocRelation {
    @EmbeddedId
    private KbDocRelationId id;

    @MapsId("sourceDocId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "source_doc_id", nullable = false)
    private KbDocument sourceDoc;

    @MapsId("targetDocId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "target_doc_id", nullable = false)
    private KbDocument targetDoc;

    @Size(max = 16)
    @ColumnDefault("'link'")
    @Column(name = "relation_type", length = 16)
    private String relationType;

    @ColumnDefault("now()")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;


}