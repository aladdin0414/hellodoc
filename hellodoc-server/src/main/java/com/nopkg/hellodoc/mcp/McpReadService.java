package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.entities.KbDocument;
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
    private final SearchService search;
    private final DocumentService documents;
    private final PermissionChecker permissions;

    public McpReadService(SearchService search, DocumentService documents, PermissionChecker permissions) {
        this.search = search; this.documents = documents; this.permissions = permissions;
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

    @Transactional(readOnly = true)
    public Map<String, Object> read(long user, long documentId, int offset) {
        if (documentId < 1 || offset < 0) throw new IllegalArgumentException();
        try {
            permissions.checkDocRole(user, documentId, DocRole.VIEWER);
            var doc = documents.getById(documentId);
            if (doc.getDeletedAt() != null || doc.getKb() == null || Boolean.TRUE.equals(doc.getIsEncrypted())) throw new IllegalStateException();
            if (doc.getType() == DocType.FOLDER) return readFolder(user, doc, offset);
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
            result.put("content", body.substring(begin, end)); result.put("hasMore", endOffset < count);
            result.put("nextOffset", endOffset < count ? endOffset : null);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Document unavailable");
        }
    }

    private Map<String, Object> readFolder(long user, KbDocument folder, int offset) {
        long kbId = folder.getKb().getId();
        var children = documents.getTree(kbId).stream()
                .filter(child -> child.getDeletedAt() == null && child.getParent() != null
                        && folder.getId().equals(child.getParent().getId()))
                .filter(child -> !Boolean.TRUE.equals(child.getIsEncrypted()))
                .filter(child -> permissions.hasDocRole(user, child.getId(), DocRole.VIEWER))
                .sorted(java.util.Comparator.comparing(KbDocument::getOrderNum,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(KbDocument::getId))
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
        result.put("documentId", folder.getId()); result.put("title", folder.getName());
        result.put("type", "folder"); result.put("knowledgeBaseId", kbId);
        result.put("knowledgeBaseTitle", folder.getKb().getTitle());
        result.put("status", folder.getStatus() == null ? null : folder.getStatus().getValue());
        result.put("path", "/kb/" + kbId + "/" + folder.getId());
        result.put("message", "这是文件夹，没有正文。"); result.put("children", page);
        result.put("hasMore", to < children.size()); result.put("nextOffset", to < children.size() ? to : null);
        return result;
    }
}
