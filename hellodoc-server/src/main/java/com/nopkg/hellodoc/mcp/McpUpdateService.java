package com.nopkg.hellodoc.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.PermissionChecker;
import com.nopkg.hellodoc.web.dto.kb.DocUpdateDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpUpdateService {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final KbDocumentRepository documents;
    private final DocumentService documentService;
    private final PermissionChecker permissions;
    private final McpDocumentUpdateOperationRepository operations;
    private final ObjectMapper mapper;
    @PersistenceContext private EntityManager entityManager;

    public McpUpdateService(KbDocumentRepository documents, DocumentService documentService,
                            PermissionChecker permissions, McpDocumentUpdateOperationRepository operations, ObjectMapper mapper) {
        this.documents = documents; this.documentService = documentService; this.permissions = permissions;
        this.operations = operations; this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> update(long user, long documentId, int expectedVersion, String mode, String markdown, String requestId) {
        if (documentId < 1 || expectedVersion < 0 || mode == null || !(mode.equals("replace") || mode.equals("append"))
                || markdown == null || (mode.equals("append") && markdown.isEmpty())
                || requestId == null || requestId.isBlank() || requestId.length() > 128) throw new McpUpdateException("INVALID_ARGUMENT");
        if (markdown.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new McpUpdateException("TOO_LARGE");
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(List.of(mode, expectedVersion, markdown)))); }
        catch (Exception e) { throw new IllegalStateException("Unable to fingerprint update", e); }

        var doc = documents.findByIdForUpdate(documentId).orElseThrow(() -> new McpUpdateException("DOCUMENT_UNAVAILABLE"));
        entityManager.refresh(doc, LockModeType.PESSIMISTIC_WRITE);
        if (doc.getDeletedAt() != null || doc.getKb() == null || doc.getType() != DocType.FILE
                || Boolean.TRUE.equals(doc.getIsEncrypted()) || !permissions.hasDocRole(user, documentId, DocRole.EDITOR)) {
            throw new McpUpdateException("DOCUMENT_UNAVAILABLE");
        }
        var existing = operations.findByUserIdAndDocument_IdAndRequestId(user, documentId, requestId);
        if (existing.isPresent()) {
            var op = existing.get();
            if (!op.getInputHash().equals(hash)) throw new McpUpdateException("REQUEST_ID_CONFLICT");
            return result(doc.getKb().getId(), documentId, op.getResultVersion(), op.getResultUpdatedAt());
        }
        int version = doc.getCurrentVersion() == null ? 0 : doc.getCurrentVersion();
        if (version != expectedVersion) throw new McpUpdateException("VERSION_CONFLICT");
        String previous = mode.equals("append") ? documentService.getContent(documentId) : "";
        String body = mode.equals("append") ? (previous == null ? "" : previous) + markdown : markdown;
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new McpUpdateException("TOO_LARGE");
        var dto = new DocUpdateDTO(); dto.setContent(body); dto.setExpectedVersion(version);
        var updated = documentService.update(user, doc.getKb().getId(), documentId, dto);
        var op = new McpDocumentUpdateOperation(); op.setUserId(user); op.setDocument(updated); op.setRequestId(requestId);
        op.setInputHash(hash); op.setResultVersion(updated.getCurrentVersion()); op.setResultUpdatedAt(updated.getUpdatedAt());
        operations.save(op); entityManager.flush();
        return result(updated.getKb().getId(), documentId, op.getResultVersion(), op.getResultUpdatedAt());
    }

    private Map<String, Object> result(long kbId, long docId, int version, java.time.OffsetDateTime updatedAt) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("documentId", docId); output.put("knowledgeBaseId", kbId); output.put("version", version);
        output.put("updatedAt", updatedAt.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString()); output.put("path", "/kb/" + kbId + "/" + docId);
        return output;
    }
}
