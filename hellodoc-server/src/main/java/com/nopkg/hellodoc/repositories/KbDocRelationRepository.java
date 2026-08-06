package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbDocRelation;
import com.nopkg.hellodoc.entities.KbDocRelationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbDocRelationRepository extends JpaRepository<KbDocRelation, KbDocRelationId> {
    List<KbDocRelation> findByIdSourceDocId(Long sourceDocId);

    List<KbDocRelation> findByIdTargetDocId(Long targetDocId);

    boolean existsByIdSourceDocIdAndIdTargetDocId(Long sourceDocId, Long targetDocId);

    void deleteByIdSourceDocIdAndIdTargetDocId(Long sourceDocId, Long targetDocId);

    void deleteByIdSourceDocIdOrIdTargetDocId(Long sourceDocId, Long targetDocId);
}
