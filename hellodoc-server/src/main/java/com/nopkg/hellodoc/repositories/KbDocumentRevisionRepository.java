package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbDocumentRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nopkg.hellodoc.enums.RevisionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface KbDocumentRevisionRepository extends JpaRepository<KbDocumentRevision, Long> {
    List<KbDocumentRevision> findByDocIdOrderByVersionDesc(Long docId);

    Optional<KbDocumentRevision> findTopByDocIdOrderByVersionDesc(Long docId);

    Optional<KbDocumentRevision> findByDocIdAndVersion(Long docId, Integer version);

    @Query("SELECT r FROM KbDocumentRevision r WHERE r.doc.id = :docId")
    Page<KbDocumentRevision> findByDocId(@Param("docId") Long docId, Pageable pageable);

    List<KbDocumentRevision> findByDocIdAndRevisionTypeOrderByVersionDesc(Long docId, RevisionType revisionType);

    @Query("SELECT r.doc.id FROM KbDocumentRevision r WHERE r.revisionType = 'AUTO' GROUP BY r.doc.id HAVING COUNT(r) > :limit")
    List<Long> findDocsExceedingAutoLimit(@Param("limit") long limit);

    List<KbDocumentRevision> findByCreatedAtBeforeAndArchivedAtIsNull(LocalDateTime threshold);
}
