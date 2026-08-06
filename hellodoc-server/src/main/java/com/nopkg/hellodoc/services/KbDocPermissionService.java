package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbDocPermission;
import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.TargetType;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.exceptions.ResourceNotFoundException;
import com.nopkg.hellodoc.repositories.KbDocPermissionRepository;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KbDocPermissionService {

    private final KbDocPermissionRepository permissionRepository;
    private final KbDocumentRepository documentRepository;

    public List<KbDocPermission> listPermissions(Long docId) {
        return permissionRepository.findByDocId(docId);
    }

    @Transactional
    public KbDocPermission addPermission(Long docId, TargetType targetType, Long targetId, DocRole role,
            OffsetDateTime expiresAt) {
        KbDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));

        KbDocPermission permission = permissionRepository
                .findByDocIdAndTargetTypeAndTargetId(docId, targetType, targetId)
                .orElseGet(() -> {
                    KbDocPermission created = new KbDocPermission();
                    created.setDoc(doc);
                    created.setTargetType(targetType);
                    created.setTargetId(targetId);
                    created.setCreatedAt(OffsetDateTime.now());
                    return created;
                });

        permission.setRole(role);
        permission.setExpiresAt(expiresAt);
        return permissionRepository.save(permission);
    }

    @Transactional
    public KbDocPermission createShareLink(Long docId, DocRole role, OffsetDateTime expiresAt) {
        KbDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));

        KbDocPermission permission = new KbDocPermission();
        permission.setDoc(doc);
        permission.setTargetType(TargetType.LINK);
        permission.setLinkToken(UUID.randomUUID().toString().replace("-", ""));
        permission.setRole(role);
        permission.setExpiresAt(expiresAt);
        permission.setCreatedAt(OffsetDateTime.now());
        return permissionRepository.save(permission);
    }

    @Transactional
    public void removePermission(Long permissionId) {
        permissionRepository.deleteById(permissionId);
    }

    @Transactional
    public void removeDocPermissions(Long docId) {
        List<KbDocPermission> permissions = permissionRepository.findByDocId(docId);
        permissionRepository.deleteAll(permissions);
    }

    public boolean hasPermission(Long docId, Long userId, DocRole requiredRole) {
        List<KbDocPermission> permissions = permissionRepository.findByDocId(docId);
        OffsetDateTime now = OffsetDateTime.now();
        for (KbDocPermission p : permissions) {
            // Check expiration
            if (p.getExpiresAt() != null && p.getExpiresAt().isBefore(now)) {
                continue;
            }

            if (p.getTargetType() == TargetType.USER && p.getTargetId().equals(userId)) {
                if (checkRole(p.getRole(), requiredRole))
                    return true;
            }
        }
        return false;
    }

    private boolean checkRole(DocRole actual, DocRole required) {
        if (actual == DocRole.EDITOR)
            return true; // Editor has all permissions
        return actual == required;
    }
}
