package com.nopkg.hellodoc.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.PermissionChecker;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class McpWriteService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final McpWriteGuard guard;
    private final AiItemService items;
    private final DocumentService docs;
    private final PermissionChecker permissions;
    private final EntityManager entityManager;

    public McpWriteService(McpWriteGuard guard, AiItemService items, DocumentService docs,
                           PermissionChecker permissions, EntityManager entityManager) {
        this.guard = guard;
        this.items = items;
        this.docs = docs;
        this.permissions = permissions;
        this.entityManager = entityManager;
    }

    @org.springframework.transaction.annotation.Transactional
    public long createItem(long user, long kb, String name, String description, List<String> aliases, String requestId) {
        permissions.checkKbRole(user, kb, KbRole.EDITOR);
        Objects.requireNonNull(name, "name");
        List<String> safeAliases = aliases == null ? List.of() : List.copyOf(aliases);
        String safeDescription = description == null ? "" : description;
        String hash = fingerprint(List.of(name, safeDescription, safeAliases));
        return guard.execute(user, kb, "item", requestId, hash, () -> {
            String target = normalize(name);
            boolean exists = docs.getTree(kb).stream()
                    .filter(doc -> doc.getParent() == null && doc.getType() == DocType.FOLDER && doc.getDeletedAt() == null)
                    .filter(doc -> doc.getExtraMeta() != null && doc.getExtraMeta().get("hellodoc_ai") instanceof Map<?, ?>)
                    .filter(doc -> Boolean.TRUE.equals(((Map<?, ?>) doc.getExtraMeta().get("hellodoc_ai")).get("item")))
                    .anyMatch(doc -> normalize(doc.getName()).equals(target));
            if (exists) throw new IllegalArgumentException("An item with the same name already exists; choose an existing candidate");
            long id = items.createItem(user, kb, name, safeDescription, safeAliases, requestMeta("item", requestId, hash)).id();
            entityManager.flush();
            return id;
        });
    }

    @org.springframework.transaction.annotation.Transactional
    public long createDocument(long user, long kb, long item, String name, String markdown, String requestId) {
        items.validateWritableItem(user, kb, item);
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(markdown, "markdown");
        String hash = fingerprint(List.of(item, name, markdown));
        return guard.execute(user, kb, "document", requestId, hash, () -> {
            long id = items.createDocument(user, kb, item, name, markdown, requestMeta("document", requestId, hash)).getId();
            entityManager.flush();
            return id;
        });
    }

    @org.springframework.transaction.annotation.Transactional
    public long createDocumentInFolder(long user, long kb, Long parentId, String name, String markdown, String requestId) {
        items.validateWritableFolder(user, kb, parentId);
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(markdown, "markdown");
        String hash = fingerprint(java.util.Arrays.asList(parentId, name, markdown));
        return guard.execute(user, kb, "document", requestId, hash, () -> {
            long id = items.createDocumentInFolder(user, kb, parentId, name, markdown, requestMeta("document", requestId, hash)).getId();
            entityManager.flush(); return id;
        });
    }

    @org.springframework.transaction.annotation.Transactional
    public long createFolder(long user, long kb, Long parentId, String name, String requestId) {
        items.validateWritableFolder(user, kb, parentId);
        Objects.requireNonNull(name, "name");
        String hash = fingerprint(java.util.Arrays.asList(parentId, name));
        return guard.execute(user, kb, "folder", requestId, hash, () -> {
            long id = items.createFolder(user, kb, parentId, name, requestMeta("folder", requestId, hash)).getId();
            entityManager.flush(); return id;
        });
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.OptionalLong findOperation(long user, long kb, String operation, String requestId) {
        permissions.checkKbRole(user, kb, KbRole.VIEWER);
        var result = guard.find(user, kb, operation, requestId);
        if (result.isPresent()) permissions.checkDocRole(user, result.getAsLong(), com.nopkg.hellodoc.enums.DocRole.VIEWER);
        return result;
    }

    private static Map<String, Object> requestMeta(String type, String id, String hash) {
        return Map.of("hellodoc_mcp_request", Map.of("type", type, "id", id, "hash", hash));
    }

    private static String normalize(String name) {
        return Normalizer.normalize(name.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static String fingerprint(Object value) {
        try {
            byte[] data = JSON.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to fingerprint MCP request", e);
        }
    }
}
