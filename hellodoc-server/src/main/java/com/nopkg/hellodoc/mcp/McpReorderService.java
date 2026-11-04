package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.DocType;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.services.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class McpReorderService {
    private final KbDocumentRepository repository;
    private final PermissionChecker permissions;
    private final DirectoryRevision revisions;

    public McpReorderService(KbDocumentRepository repository, PermissionChecker permissions, DirectoryRevision revisions) {
        this.repository = repository; this.permissions = permissions; this.revisions = revisions;
    }

    @Transactional
    public Map<String, Object> reorder(long user, long kbId, long parentId, String snapshot, List<Long> orderedIds) {
        if (snapshot == null || snapshot.isBlank()) throw new IllegalArgumentException("INVALID_ARGUMENT");
        return reorderInternal(user, kbId, parentId, snapshot, null, orderedIds);
    }

    @Transactional
    public Map<String, Object> reorderWithExpectedOrder(long user, long kbId, long parentId, List<Long> expectedIds, List<Long> orderedIds) {
        if (expectedIds == null || expectedIds.size() > 10000) throw new IllegalArgumentException("INVALID_ARGUMENT");
        return reorderInternal(user, kbId, parentId, null, expectedIds, orderedIds);
    }

    private Map<String, Object> reorderInternal(long user, long kbId, long parentId, String snapshot, List<Long> expectedIds, List<Long> orderedIds) {
        if (kbId < 1 || parentId < 0 || orderedIds == null)
            throw new IllegalArgumentException("INVALID_ARGUMENT");
        if (orderedIds.size() > 10000) throw new IllegalArgumentException("DIRECTORY_TOO_LARGE");
        var kb = revisions.lock(kbId);
        if (parentId == 0) {
            if (!permissions.hasKbRole(user, kbId, KbRole.EDITOR)) throw new IllegalStateException("DIRECTORY_UNAVAILABLE");
        } else {
            var parent = repository.findByIdAndDeletedAtIsNull(parentId).orElseThrow(() -> new IllegalStateException("DIRECTORY_UNAVAILABLE"));
            if (parent.getKb() == null || parent.getKb().getId() != kbId || parent.getType() != DocType.FOLDER
                    || Boolean.TRUE.equals(parent.getIsEncrypted()) || !permissions.hasDocRole(user, parentId, DocRole.EDITOR))
                throw new IllegalStateException("DIRECTORY_UNAVAILABLE");
        }
        List<KbDocument> children = (parentId == 0 ? repository.findByKbIdAndParentIsNull(kbId)
                : repository.findByKbIdAndParentIdAndDeletedAtIsNull(kbId, parentId)).stream()
                .filter(d -> d.getDeletedAt() == null).toList();
        if (children.size() > 10000) throw new IllegalStateException("DIRECTORY_TOO_LARGE");
        if (children.stream().anyMatch(d -> Boolean.TRUE.equals(d.getIsEncrypted()) ||
                !permissions.hasDocRole(user, d.getId(), DocRole.EDITOR))) throw new IllegalStateException("DIRECTORY_UNAVAILABLE");
        var current = DirectoryOrder.ids(children);
        boolean changed = !current.equals(orderedIds);
        if (changed && ((expectedIds != null && !current.equals(expectedIds))
                || (snapshot != null && !Long.toString(kb.getTreeRevision()).equals(snapshot)))) throw new IllegalStateException("ORDER_CONFLICT");
        if (!DirectoryOrder.isPermutation(current, orderedIds)) throw new IllegalArgumentException("INVALID_ORDER");
        String next = Long.toString(kb.getTreeRevision());
        if (changed) {
            var byId = children.stream().collect(Collectors.toMap(KbDocument::getId, Function.identity()));
            for (int i = 0; i < orderedIds.size(); i++) byId.get(orderedIds.get(i)).setOrderNum((i + 1) * 10000);
            repository.saveAll(children);
            next = revisions.bump(kb);
        }
        return Map.of("knowledgeBaseId", kbId, "parentId", parentId, "changed", changed, "snapshot", next, "orderedIds", orderedIds);
    }
}
