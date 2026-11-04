package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.PermissionChecker;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class McpMoveService {
    private final KbDocumentRepository repository;
    private final DocumentService documents;
    private final PermissionChecker permissions;
    private final AiItemService items;
    private final DirectoryRevision revisions;
    @PersistenceContext private EntityManager entityManager;

    public McpMoveService(KbDocumentRepository repository, DocumentService documents, PermissionChecker permissions, AiItemService items, DirectoryRevision revisions) {
        this.repository = repository; this.documents = documents; this.permissions = permissions; this.items = items; this.revisions = revisions;
    }

    @Transactional
    public Map<String, Object> move(long user, long documentId, long expectedParentId, long parentId) {
        if (documentId < 1 || expectedParentId < 0 || parentId < 0) throw new McpMoveException("INVALID_ARGUMENT");
        var initial = repository.findByIdAndDeletedAtIsNull(documentId).orElseThrow(() -> new McpMoveException("DOCUMENT_UNAVAILABLE"));
        if (initial.getKb() == null) throw new McpMoveException("DOCUMENT_UNAVAILABLE");
        var lockedDirectory = revisions.lock(initial.getKb().getId());
        var doc = repository.findByIdForUpdate(documentId).orElseThrow(() -> new McpMoveException("DOCUMENT_UNAVAILABLE"));
        entityManager.refresh(doc, LockModeType.PESSIMISTIC_WRITE);
        if (doc.getKb() == null || doc.getKb().getDeletedAt() != null || doc.getDeletedAt() != null
                || doc.getType() != DocType.FILE || Boolean.TRUE.equals(doc.getIsEncrypted())
                || !permissions.hasDocRole(user, documentId, DocRole.EDITOR)) throw new McpMoveException("DOCUMENT_UNAVAILABLE");
        long kb = doc.getKb().getId();
        Long targetId = parentId == 0 ? null : parentId;
        if (targetId != null) {
            var target = repository.findByIdForUpdate(targetId).orElseThrow(() -> new McpMoveException("DESTINATION_UNAVAILABLE"));
            entityManager.refresh(target, LockModeType.PESSIMISTIC_WRITE);
        }
        try { items.validateWritableFolder(user, kb, targetId); }
        catch (Exception e) { throw new McpMoveException("DESTINATION_UNAVAILABLE"); }
        long currentParentId = doc.getParent() == null ? 0 : doc.getParent().getId();
        if (currentParentId != parentId) {
            if (currentParentId != expectedParentId) throw new McpMoveException("LOCATION_CONFLICT");
            doc.setParent(targetId == null ? null : documents.getById(targetId));
            doc.setUpdatedAt(OffsetDateTime.now());
            revisions.bump(lockedDirectory);
            repository.save(doc); entityManager.flush();
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("documentId", documentId); result.put("knowledgeBaseId", kb);
        result.put("parentId", targetId); result.put("path", "/kb/" + kb + "/" + documentId);
        return result;
    }
}
