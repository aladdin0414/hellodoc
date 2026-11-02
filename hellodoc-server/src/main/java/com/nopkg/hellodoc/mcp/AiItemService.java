package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.kb.DocCreateDTO;
import org.springframework.transaction.annotation.Transactional;
import com.nopkg.hellodoc.services.DocumentService;
import com.nopkg.hellodoc.services.PermissionChecker;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AiItemService {
    private final DocumentService documents;
    private final PermissionChecker permissions;

    public AiItemService(DocumentService documents, PermissionChecker permissions) {
        this.documents = documents;
        this.permissions = permissions;
    }

    public record Item(long id, String name, String description, List<String> aliases) {}

    @Transactional(readOnly = true)
    public List<Item> listItems(long userId, long kbId) {
        permissions.checkKbRole(userId, kbId, KbRole.VIEWER);
        return documents.getTree(kbId).stream()
                .filter(doc -> doc.getType() == DocType.FOLDER && doc.getParent() == null && doc.getDeletedAt() == null)
                .filter(doc -> doc.getKb() != null && Objects.equals(doc.getKb().getId(), kbId))
                .filter(doc -> metadata(doc).get("item") == Boolean.TRUE)
                .filter(doc -> permissions.hasDocRole(userId, doc.getId(), DocRole.VIEWER))
                .sorted(java.util.Comparator.comparing(KbDocument::getId))
                .map(this::toItem)
                .toList();
    }

    @Transactional
    public Item createItem(long userId, long kbId, String name, String description, List<String> aliases) {
        return createItem(userId, kbId, name, description, aliases, Map.of());
    }

    Item createItem(long userId, long kbId, String name, String description, List<String> aliases, Map<String, Object> requestMeta) {
        permissions.checkKbRole(userId, kbId, KbRole.EDITOR);
        DocCreateDTO dto = new DocCreateDTO();
        dto.setName(requireTitle(name));
        dto.setType(DocType.FOLDER);
        var extra = new java.util.HashMap<String, Object>();
        extra.put("hellodoc_ai", Map.of("item", true, "description", description == null ? "" : description,
                "aliases", aliases == null ? List.of() : List.copyOf(aliases)));
        extra.putAll(requestMeta);
        dto.setExtraMeta(extra);
        return toItem(documents.create(userId, kbId, dto));
    }

    @Transactional
    public KbDocument createDocument(long userId, long kbId, long itemId, String name, String content) {
        return createDocument(userId, kbId, itemId, name, content, Map.of());
    }

    KbDocument createDocument(long userId, long kbId, long itemId, String name, String content, Map<String, Object> requestMeta) {
        validateWritableItem(userId, kbId, itemId);
        DocCreateDTO dto = new DocCreateDTO();
        dto.setName(requireTitle(name));
        dto.setType(DocType.FILE);
        dto.setParentId(itemId);
        dto.setContent(Objects.requireNonNull(content, "content"));
        dto.setExtraMeta(requestMeta);
        return documents.create(userId, kbId, dto);
    }

    void validateWritableItem(long userId, long kbId, long itemId) {
        permissions.checkKbRole(userId, kbId, KbRole.EDITOR);
        KbDocument item = documents.getById(itemId);
        if (item.getKb() == null || !Objects.equals(item.getKb().getId(), kbId)
                || item.getType() != DocType.FOLDER || item.getParent() != null
                || item.getDeletedAt() != null || metadata(item).get("item") != Boolean.TRUE) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "Item is not an active root folder in this knowledge base");
        }
        permissions.checkDocRole(userId, itemId, DocRole.EDITOR);
    }

    private static String requireTitle(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 255) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "Name must contain 1 to 255 characters");
        }
        return name.trim();
    }

    private Item toItem(KbDocument doc) {
        Map<?, ?> meta = metadata(doc);
        String description = meta.get("description") instanceof String value ? value : "";
        List<String> aliases = meta.get("aliases") instanceof List<?> values
                ? values.stream().filter(String.class::isInstance).map(String.class::cast).toList() : List.of();
        return new Item(doc.getId(), doc.getName(), description, aliases);
    }

    private static Map<?, ?> metadata(KbDocument doc) {
        if (doc.getExtraMeta() == null) return Map.of();
        return doc.getExtraMeta().get("hellodoc_ai") instanceof Map<?, ?> value ? value : Map.of();
    }
}
