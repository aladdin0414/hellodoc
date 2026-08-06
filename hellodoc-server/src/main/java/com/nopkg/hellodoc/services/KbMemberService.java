package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.KbKbMember;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.exceptions.ResourceNotFoundException;
import com.nopkg.hellodoc.repositories.KbKbMemberRepository;
import com.nopkg.hellodoc.repositories.KbKnowledgeBaseRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KbMemberService {

    private final KbKbMemberRepository memberRepository;
    private final KbKnowledgeBaseRepository kbRepository;

    public List<KbKbMember> listMembers(Long kbId) {
        return memberRepository.findByKbId(kbId);
    }

    @Transactional
    public KbKbMember addMember(Long operatorId, Long kbId, Long targetUserId, KbRole role) {
        if (kbId == null) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "kbId 不能为空");
        }
        KbKnowledgeBase kb = kbRepository.findById(kbId)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeBase", kbId));

        // Permission check: only OWNER/ADMIN can add members
        checkHasAdminPermission(operatorId, kb);

        KbKbMember member = memberRepository.findByKbIdAndUserId(kbId, targetUserId)
                .orElseGet(() -> {
                    KbKbMember created = new KbKbMember();
                    created.setKb(kb);
                    created.setUserId(targetUserId);
                    created.setCreatedAt(OffsetDateTime.now());
                    return created;
                });

        // Protected owner role
        if (targetUserId.equals(kb.getOwnerId())) {
            member.setRole(KbRole.OWNER);
        } else {
            member.setRole(role != null ? role : KbRole.VIEWER);
        }

        member.setInvitedBy(operatorId);
        member.setUpdatedAt(OffsetDateTime.now());
        return memberRepository.save(member);
    }

    @Transactional
    public KbKbMember updateMemberRole(Long operatorId, Long kbId, Long targetUserId, KbRole role) {
        KbKbMember member = memberRepository.findByKbIdAndUserId(kbId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("KbMember", targetUserId));

        if (member.getRole() == KbRole.OWNER) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "Cannot change role of the owner");
        }

        KbKnowledgeBase kb = member.getKb();
        checkHasAdminPermission(operatorId, kb);

        if (role != null) {
            member.setRole(role);
        }
        member.setUpdatedAt(OffsetDateTime.now());
        return memberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long operatorId, Long kbId, Long targetUserId) {
        KbKbMember member = memberRepository.findByKbIdAndUserId(kbId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("KbMember", targetUserId));

        if (member.getRole() == KbRole.OWNER) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, "Cannot remove the owner");
        }

        KbKnowledgeBase kb = member.getKb();
        // User can remove themselves, or an ADMIN can remove others
        if (!operatorId.equals(targetUserId)) {
            checkHasAdminPermission(operatorId, kb);
        }

        memberRepository.delete(member);
    }

    public KbRole getMemberRole(Long kbId, Long userId) {
        return memberRepository.findByKbIdAndUserId(kbId, userId)
                .map(KbKbMember::getRole)
                .orElse(null);
    }

    public boolean hasPermission(Long kbId, Long userId, KbRole requiredRole) {
        KbRole role = getMemberRole(kbId, userId);
        if (role == null)
            return false;

        // Ownership check
        if (role == KbRole.OWNER)
            return true;

        switch (requiredRole) {
            case OWNER:
                return role == KbRole.OWNER;
            case ADMIN:
                return role == KbRole.ADMIN;
            case EDITOR:
                return role == KbRole.ADMIN || role == KbRole.EDITOR;
            case VIEWER:
                return true; // Any member has at least viewer
            default:
                return false;
        }
    }

    private void checkHasAdminPermission(Long userId, KbKnowledgeBase kb) {
        if (kb.getOwnerId().equals(userId))
            return;

        KbRole role = getMemberRole(kb.getId(), userId);
        if (role != KbRole.ADMIN) {
            throw new BusinessException(ApiResponse.Code.NO_PERMISSION, "Only owner or admin can perform this action");
        }
    }
}
