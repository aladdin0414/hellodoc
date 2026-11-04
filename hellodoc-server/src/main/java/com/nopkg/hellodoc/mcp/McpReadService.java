package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.PermissionChecker;
import com.nopkg.hellodoc.services.SearchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpReadService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final SearchService search;
    private final DocumentService documents;
    private final PermissionChecker permissions;
    private final KbDocumentRepository documentRepository;
    private final DirectoryRevision revisions;

    public McpReadService(SearchService search, DocumentService documents, PermissionChecker permissions, KbDocumentRepository documentRepository, DirectoryRevision revisions) {
        this.search = search; this.documents = documents; this.permissions = permissions; this.documentRepository = documentRepository; this.revisions = revisions;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(long user, String query, int limit) {
        if (query == null || query.isBlank() || limit < 1 || limit > 100) throw new IllegalArgumentException();
        return search.searchAll(user, query, limit).stream().map(result -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("documentId", result.getDocId()); item.put("title", result.getDocName());
            item.put("type", result.getDocType());
            item.put("knowledgeBaseId", result.getKbId()); item.put("knowledgeBaseTitle", result.getKbTitle());
            item.put("status", result.getStatus()); item.put("snippet", result.getSnippet());
            item.put("path", "/kb/" + result.getKbId() + "/" + result.getDocId());
            return item;
        }).toList();
    }

    @Transactional
    public Map<String, Object> read(long user, long documentId, int offset, Integer expectedVersion) {
        if (documentId < 1 || offset < 0) throw new IllegalArgumentException();
        try {
            var doc = documentRepository.findByIdForUpdate(documentId).orElseThrow();
            entityManager.refresh(doc, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            permissions.checkDocRole(user, documentId, DocRole.VIEWER);
            if (doc.getDeletedAt() != null || doc.getKb() == null || Boolean.TRUE.equals(doc.getIsEncrypted())) throw new IllegalStateException();
            if (doc.getType() == DocType.FOLDER) return readFolder(user, doc, offset);
            int version = doc.getCurrentVersion() == null ? 0 : doc.getCurrentVersion();
            if (offset > 0 && expectedVersion == null) throw new IllegalArgumentException("expectedVersion required");
            if (expectedVersion != null && expectedVersion != version) throw new McpVersionConflictException();
            String body = documents.getContent(documentId);
            if (body == null) body = "";
            int count = body.codePointCount(0, body.length());
            int beginOffset = Math.min(offset, count);
            int endOffset = (int) Math.min((long) count, (long) beginOffset + 12000);
            int begin = body.offsetByCodePoints(0, beginOffset);
            int end = body.offsetByCodePoints(0, endOffset);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("documentId", documentId); result.put("title", doc.getName());
            result.put("type", doc.getType() == null ? null : doc.getType().getValue());
            result.put("knowledgeBaseId", doc.getKb().getId()); result.put("knowledgeBaseTitle", doc.getKb().getTitle());
            result.put("status", doc.getStatus() == null ? null : doc.getStatus().getValue());
            result.put("path", "/kb/" + doc.getKb().getId() + "/" + documentId);
            result.put("version", version); result.put("updatedAt", doc.getUpdatedAt());
            result.put("currentParentId", doc.getParent() == null ? 0L : doc.getParent().getId());
            result.put("content", body.substring(begin, end)); result.put("hasMore", endOffset < count);
            result.put("nextOffset", endOffset < count ? endOffset : null);
            return result;
        } catch (IllegalArgumentException | McpVersionConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Document unavailable");
        }
    }

    @Transactional
    public Map<String, Object> listFolderChildren(long user, long kbId, Long parentId, int offset) {
        return listFolderChildren(user, kbId, parentId, offset, null);
    }

    @Transactional
    public Map<String, Object> listFolderChildren(long user, long kbId, Long parentId, int offset, String expectedSnapshot) {
        if (kbId < 1 || offset < 0 || (parentId != null && parentId < 1)) throw new IllegalArgumentException();
        var kb = revisions.lock(kbId);
        String snapshot = Long.toString(kb.getTreeRevision());
        if (parentId == null) {
            permissions.checkKbRole(user, kbId, com.nopkg.hellodoc.enums.KbRole.VIEWER);
            if (expectedSnapshot != null && !expectedSnapshot.equals(snapshot)) throw new IllegalStateException("ORDER_CONFLICT");
            var result = folderChildren(user, kbId, null, offset);
            result.put("snapshot", snapshot);
            return result;
        }
        var result = read(user, parentId, offset, null);
        if (!"folder".equals(result.get("type")) || !Long.valueOf(kbId).equals(result.get("knowledgeBaseId")))
            throw new IllegalStateException("Folder unavailable");
        if (expectedSnapshot != null && !expectedSnapshot.equals(snapshot)) throw new IllegalStateException("ORDER_CONFLICT");
        result.put("snapshot", snapshot);
        return result;
    }

    private Map<String, Object> folderChildren(long user, long kbId, Long parentId, int offset) {
        var children = documents.getTree(kbId).stream()
                .filter(child -> child.getDeletedAt() == null && (parentId == null ? child.getParent() == null
                        : child.getParent() != null && parentId.equals(child.getParent().getId())))
                .filter(child -> !Boolean.TRUE.equals(child.getIsEncrypted()))
                .filter(child -> permissions.hasDocRole(user, child.getId(), DocRole.VIEWER))
                .sorted(DirectoryOrder.comparator())
                .toList();
        int from = Math.min(offset, children.size());
        int to = (int) Math.min((long) children.size(), (long) from + 50);
        List<Map<String, Object>> page = children.subList(from, to).stream().map(child -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("documentId", child.getId()); item.put("title", child.getName());
            item.put("type", child.getType() == null ? null : child.getType().getValue());
            item.put("status", child.getStatus() == null ? null : child.getStatus().getValue());
            item.put("path", "/kb/" + kbId + "/" + child.getId());
            return item;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledgeBaseId", kbId); result.put("parentId", parentId); result.put("children", page);
        result.put("hasMore", to < children.size()); result.put("nextOffset", to < children.size() ? to : null);
        return result;
    }

    private Map<String, Object> readFolder(long user, KbDocument folder, int offset) {
        long kbId = folder.getKb().getId();
        Map<String, Object> result = folderChildren(user, kbId, folder.getId(), offset);
        result.put("documentId", folder.getId()); result.put("title", folder.getName());
        result.put("currentParentId", folder.getParent() == null ? 0 : folder.getParent().getId());
        result.put("type", "folder"); result.put("knowledgeBaseId", kbId);
        result.put("knowledgeBaseTitle", folder.getKb().getTitle());
        result.put("status", folder.getStatus() == null ? null : folder.getStatus().getValue());
        result.put("path", "/kb/" + kbId + "/" + folder.getId());
        result.put("message", "这是文件夹，没有正文。");
        return result;
    }
}
