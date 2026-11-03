package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.enums.TargetType;
import com.nopkg.hellodoc.repositories.KbDocPermissionRepository;
import com.nopkg.hellodoc.repositories.KbKnowledgeBaseRepository;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.services.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class McpKnowledgeBaseService {
    private final KbService service;
    private final KbKnowledgeBaseRepository repositories;
    private final KbDocPermissionRepository grants;
    private final PermissionChecker permissions;
    public McpKnowledgeBaseService(KbService service, KbKnowledgeBaseRepository repositories,
                                   KbDocPermissionRepository grants, PermissionChecker permissions) {
        this.service = service; this.repositories = repositories; this.grants = grants; this.permissions = permissions;
    }
    @Transactional(readOnly = true)
    public Map<String, Object> list(long user, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException();
        Map<Long, KbKnowledgeBase> candidates = new TreeMap<>();
        service.listAccessibleKnowledgeBases(user).forEach(kb -> candidates.put(kb.getId(), kb));
        service.listPublicKbs().forEach(kb -> candidates.put(kb.getId(), kb));
        repositories.findByAllowAnonymousTrueAndDeletedAtIsNull().forEach(kb -> candidates.put(kb.getId(), kb));
        for (var grant : grants.findByTargetTypeAndTargetId(TargetType.USER, user)) {
            var doc = grant.getDoc();
            if (doc != null && doc.getKb() != null && doc.getKb().getDeletedAt() == null
                    && doc.getDeletedAt() == null && !Boolean.TRUE.equals(doc.getIsEncrypted())
                    && permissions.hasDocRole(user, doc.getId(), DocRole.VIEWER)) candidates.put(doc.getKb().getId(), doc.getKb());
        }
        var all = candidates.values().stream().map(kb -> Map.<String,Object>of(
                "knowledgeBaseId", kb.getId(), "title", kb.getTitle(),
                "canBrowseRoot", permissions.hasKbRole(user, kb.getId(), KbRole.VIEWER))).toList();
        int from = Math.min(offset, all.size()); int to = (int)Math.min((long)all.size(), (long)from + limit);
        Map<String,Object> result = new LinkedHashMap<>(); result.put("knowledgeBases", all.subList(from, to));
        result.put("hasMore", to < all.size()); result.put("nextOffset", to < all.size() ? to : null); return result;
    }
}
