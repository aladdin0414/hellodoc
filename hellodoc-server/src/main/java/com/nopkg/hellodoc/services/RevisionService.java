package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbDocumentRevision;
import com.nopkg.hellodoc.enums.RevisionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface RevisionService {

    KbDocumentRevision createRevision(Long docId, String content, RevisionType type, String message, Long userId);

    KbDocumentRevision autoSave(Long docId, String content, Long userId);

    void createMilestone(Long docId, String message, Long userId);

    Page<KbDocumentRevision> getRevisionHistory(Long docId, Pageable pageable);

    KbDocumentRevision getRevision(Long docId, Integer version);

    String getRevisionContent(Long docId, Integer version);

    void restoreRevision(Long docId, Integer version, Long userId);

    String compareRevisions(Long docId, Integer v1, Integer v2);

    void cleanupAndArchiveRevisions();
}
