package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbDocument;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.enums.PublicRole;
import com.nopkg.hellodoc.enums.Visibility;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.exceptions.ResourceNotFoundException;
import com.nopkg.hellodoc.repositories.KbDocumentRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionChecker {

    private final KbMemberService memberService;
    private final KbDocPermissionService docPermissionService;
    private final KbService kbService;
    private final KbDocumentRepository documentRepository;

    public void checkKbRole(Long userId, Long kbId, KbRole requiredRole) {
        if (hasKbRole(userId, kbId, requiredRole))
            return;
        throw new BusinessException(ApiResponse.Code.NO_PERMISSION, com.nopkg.hellodoc.utils.MessageUtils.get("auth.kb_role_required", "Requires knowledge base role: ") + requiredRole);
    }

    public boolean hasKbRole(Long userId, Long kbId, KbRole requiredRole) {
        if (kbId == null) {
            return false;
        }
        KbKnowledgeBase kb = kbService.getKnowledgeBase(kbId);

        // 1. 所有者旁路（Owner bypass）
        if (userId != null && userId.equals(kb.getOwnerId()))
            return true;

        // 2. 成员检查
        if (userId != null && memberService.hasPermission(kbId, userId, requiredRole))
            return true;

        // 3. 公开检查
        if (kb.getVisibility() == Visibility.PUBLIC) {
            if (requiredRole == KbRole.VIEWER)
                return true;
            if (checkPublicKbRole(kb.getPublicRole(), requiredRole))
                return true;
        }

        // 4. 允许匿名检查（登录用户也应满足此条件）
        if (Boolean.TRUE.equals(kb.getAllowAnonymous())) {
            if (requiredRole == KbRole.VIEWER)
                return true;
        }

        return false;
    }

    public void checkDocRole(Long userId, Long docId, DocRole requiredRole) {
        if (hasDocRole(userId, docId, requiredRole))
            return;
        throw new BusinessException(ApiResponse.Code.NO_PERMISSION, com.nopkg.hellodoc.utils.MessageUtils.get("auth.doc_role_required", "Requires document role: ") + requiredRole);
    }

    public boolean hasDocRole(Long userId, Long docId, DocRole requiredRole) {
        if (docId == null) {
            return false;
        }
        KbDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));
        KbKnowledgeBase kb = doc.getKb();

        // 1. 文档级权限（最高优先级）
        // 如果用户被明确授予了该文档的编辑者（Editor）或查看者（Viewer）权限
        if (userId != null && docPermissionService.hasPermission(docId, userId, requiredRole)) {
            return true;
        }

        // 2. 知识库级权限
        KbRole mapRole = (requiredRole == DocRole.EDITOR) ? KbRole.EDITOR : KbRole.VIEWER;
        if (hasKbRole(userId, kb.getId(), mapRole)) {
            // 注意：如果用户拥有文档级权限，应该已在步骤 1 中捕获。
            // 如果他们来到这里，说明他们没有明确的文档级权限。
            // 但是慢着，工作流规定如果他们拥有文档级查看者权限，但拥有知识库级编辑者权限，他们获得的是查看者权限。
            // 我的步骤 1 docPermissionService.hasPermission(docId, userId, requiredRole)
            // 如果 required 是编辑器，且他们只有查看者权限，则返回 false。
            // 但如果他们拥有知识库编辑者权限，如果文档级是查看者，他们不应该获得编辑器权限。

            // 所以我应该先检查他们是否拥有任何文档级权限来覆盖知识库级。
            if (userId != null && docPermissionService.hasPermission(docId, userId, DocRole.VIEWER)) {
                // 他们拥有某种文档级权限。如果 requiredRole 是编辑器且他们来到这里，
                // 那么他们在文档级必然只有查看者权限。
                if (requiredRole == DocRole.EDITOR)
                    return false;
                return true; // 曾是查看者
            }
            return true; // 无文档级覆盖，知识库角色适用
        }

        return false;
    }

    private boolean checkPublicKbRole(PublicRole publicRole, KbRole required) {
        if (publicRole == null || publicRole == PublicRole.NONE)
            return false;
        // 公关权限（查看者）不授予 编辑者/管理员/所有者 权限
        if (required == KbRole.EDITOR || required == KbRole.ADMIN || required == KbRole.OWNER)
            return false;
        return true; // 任何非 NONE 的公开角色都满足查看者权限要求
    }
}
