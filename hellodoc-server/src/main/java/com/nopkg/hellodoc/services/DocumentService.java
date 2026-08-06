package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.web.dto.kb.DocCreateDTO;
import com.nopkg.hellodoc.web.dto.kb.DocUpdateDTO;

import java.util.List;

public interface DocumentService {
    KbDocument create(Long userId, Long kbId, DocCreateDTO dto);

    KbDocument update(Long userId, Long kbId, Long docId, DocUpdateDTO dto);

    void delete(Long userId, Long kbId, Long docId);

    void restore(Long userId, Long kbId, Long docId);

    List<KbDocument> getTrashList(Long userId, Long kbId);

    void permanentlyDelete(Long userId, Long kbId, Long docId);

    void clearTrash(Long userId, Long kbId);

    KbDocument getById(Long docId);

    KbDocument getBySlug(Long kbId, String slug);

    // Tree operations
    List<KbDocument> getTree(Long kbId);

    void move(Long userId, Long docId, Long newParentId);

    void reorder(Long userId, Long parentId, List<Long> orderedIds);

    // Content
    void updateContent(Long userId, Long docId, String content);

    String getContent(Long docId);

    String getUnprotectedContent(Long docId);

    // Revisions
    List<com.nopkg.hellodoc.entities.KbDocumentRevision> listRevisions(Long userId, Long kbId, Long docId);

    com.nopkg.hellodoc.entities.KbDocumentRevision createRevision(Long userId, Long kbId, Long docId, String content,
            String message);

    KbDocument duplicate(Long userId, Long kbId, Long docId);

    KbDocument copyToKb(Long userId, Long sourceDocId, Long targetKbId);

    void incrementViewCount(Long docId);

    void exportToZip(Long userId, Long kbId, Long docId, java.io.OutputStream outputStream);

    KbDocument unlockDocument(Long userId, Long docId, String password);
}
