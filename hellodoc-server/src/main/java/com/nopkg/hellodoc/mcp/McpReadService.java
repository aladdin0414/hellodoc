package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.enums.DocRole;
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
            String body = documents.getContent(documentId);
            if (body == null) body = "";
            int count = body.codePointCount(0, body.length());
            int beginOffset = Math.min(offset, count);
            int endOffset = (int) Math.min((long) count, (long) beginOffset + 12000);
            int begin = body.offsetByCodePoints(0, beginOffset);
            int end = body.offsetByCodePoints(0, endOffset);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("documentId", documentId); result.put("title", doc.getName());
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
}
