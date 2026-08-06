package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.KbDocumentContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

public interface KbDocumentContentRepository extends JpaRepository<KbDocumentContent, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE KbDocumentContent c SET c.content = :content, c.updatedAt = :updatedAt WHERE c.docId = :docId")
    int updateContent(Long docId, String content, OffsetDateTime updatedAt);

    java.util.Optional<KbDocumentContent> findByDocId(Long docId);
}
