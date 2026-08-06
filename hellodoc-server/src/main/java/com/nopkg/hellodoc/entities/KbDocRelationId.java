package com.nopkg.hellodoc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
public class KbDocRelationId implements Serializable {
    private static final long serialVersionUID = 1723019000744136113L;
    @NotNull
    @Column(name = "source_doc_id", nullable = false)
    private Long sourceDocId;

    @NotNull
    @Column(name = "target_doc_id", nullable = false)
    private Long targetDocId;


}