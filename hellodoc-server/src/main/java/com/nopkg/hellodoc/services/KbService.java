package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.audit.AuditAction;
import com.nopkg.hellodoc.audit.AuditTargetType;
import com.nopkg.hellodoc.audit.Auditable;
import com.nopkg.hellodoc.entities.KbKbMember;
import com.nopkg.hellodoc.entities.KbKbUserPref;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.enums.Visibility;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.exceptions.ResourceNotFoundException;
import com.nopkg.hellodoc.repositories.KbKbMemberRepository;
import com.nopkg.hellodoc.repositories.KbKbUserPrefRepository;
import com.nopkg.hellodoc.repositories.KbKnowledgeBaseRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KbService {

    private final KbKnowledgeBaseRepository kbRepository;
    private final KbKbMemberRepository memberRepository;
    private final KbKbUserPrefRepository prefRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    public Long requireUserId(String username) {
        return userService.getUserByUsername(username)
                .map(SysUser::getId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, "User not found"));
    }

    public List<KbKnowledgeBase> listAccessibleKnowledgeBases(Long userId) {
        List<KbKnowledgeBase> owned = kbRepository.findByOwnerIdAndDeletedAtIsNull(userId);
        List<KbKbMember> memberLinks = memberRepository.findByUserId(userId);
        List<Long> memberKbIds = memberLinks.stream()
                .map(link -> link.getKb().getId())
                .distinct()
                .toList();
        List<KbKnowledgeBase> memberKbs = memberKbIds.isEmpty() ? List.of()
                : kbRepository.findByIdInAndDeletedAtIsNull(memberKbIds);
        Map<Long, KbKnowledgeBase> merged = new HashMap<>();
        owned.forEach(kb -> merged.put(kb.getId(), kb));
        memberKbs.forEach(kb -> merged.put(kb.getId(), kb));
        return new ArrayList<>(merged.values());
    }

    public List<KbKnowledgeBase> listPublicKbs() {
        return kbRepository.findByVisibilityAndDeletedAtIsNull(Visibility.PUBLIC);
    }

    public List<KbKnowledgeBase> listTrash(Long userId) {
        // Simple implementation: all deleted KBs owned by user
        return kbRepository.findAll().stream()
                .filter(kb -> kb.getOwnerId().equals(userId) && kb.getDeletedAt() != null)
                .toList();
    }

    public Map<Long, KbKbUserPref> listUserPrefs(Long userId) {
        return prefRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(pref -> pref.getKb().getId(), pref -> pref));
    }

    public Map<Long, List<KbKbMember>> listMembersByKbIds(Collection<Long> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return Map.of();
        }
        List<KbKbMember> members = memberRepository.findByKbIdIn(new ArrayList<>(kbIds));
        return members.stream().collect(Collectors.groupingBy(member -> member.getKb().getId()));
    }

    public boolean isShared(Long kbId) {
        if (kbId == null)
            return false;
        KbKnowledgeBase kb = getKnowledgeBase(kbId);
        return memberRepository.findByKbId(kbId).stream()
                .anyMatch(m -> !Objects.equals(m.getUserId(), kb.getOwnerId()));
    }

    public KbKnowledgeBase getKnowledgeBase(@NonNull Long kbId) {
        return kbRepository.findById(kbId)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeBase", kbId));
    }

    public boolean canReadKb(Long userId, KbKnowledgeBase kb) {
        if (kb.getOwnerId() != null && kb.getOwnerId().equals(userId)) {
            return true;
        }
        if (kb.getVisibility() == Visibility.PUBLIC) {
            return true;
        }
        return memberRepository.findByKbIdAndUserId(kb.getId(), userId).isPresent();
    }

    public boolean canEditKb(Long userId, KbKnowledgeBase kb) {
        if (kb.getOwnerId() != null && kb.getOwnerId().equals(userId)) {
            return true;
        }
        return memberRepository.findByKbIdAndUserId(kb.getId(), userId)
                .map(member -> member.getRole() == KbRole.OWNER || member.getRole() == KbRole.ADMIN)
                .orElse(false);
    }

    public boolean canEditDocuments(Long userId, KbKnowledgeBase kb) {
        if (kb.getOwnerId() != null && kb.getOwnerId().equals(userId)) {
            return true;
        }
        return memberRepository.findByKbIdAndUserId(kb.getId(), userId)
                .map(member -> member.getRole() == KbRole.OWNER || member.getRole() == KbRole.ADMIN
                        || member.getRole() == KbRole.EDITOR)
                .orElse(false);
    }

    @Transactional
    @Auditable(targetType = AuditTargetType.KB, action = AuditAction.CREATE, targetIdExpression = "#result.id")
    public KbKnowledgeBase createKnowledgeBase(Long userId, com.nopkg.hellodoc.web.dto.kb.KbCreateDTO dto) {
        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setTitle(dto.getTitle());
        kb.setDescription(dto.getDescription());
        kb.setColor(dto.getColor());
        kb.setIcon(dto.getIcon());
        kb.setOwnerId(userId);
        kb.setAllowAnonymous(Boolean.TRUE.equals(dto.getAllowAnonymous()));
        if (dto.getVisibility() != null) {
            kb.setVisibility(dto.getVisibility());
        }
        kb.setCreatedAt(OffsetDateTime.now());
        kb.setUpdatedAt(OffsetDateTime.now());
        KbKnowledgeBase saved = kbRepository.save(kb);

        KbKbMember ownerMember = new KbKbMember();
        ownerMember.setKb(saved);
        ownerMember.setUserId(userId);
        ownerMember.setRole(KbRole.OWNER);
        ownerMember.setInvitedBy(userId);
        ownerMember.setCreatedAt(OffsetDateTime.now());
        ownerMember.setUpdatedAt(OffsetDateTime.now());
        memberRepository.save(ownerMember);

        return saved;
    }

    @Transactional
    @Auditable(targetType = AuditTargetType.KB, action = AuditAction.UPDATE, targetIdExpression = "#kbId")
    public KbKnowledgeBase updateKnowledgeBase(Long userId, Long kbId,
            com.nopkg.hellodoc.web.dto.kb.KbUpdateDTO dto) {
        KbKnowledgeBase kb = getKnowledgeBase(Objects.requireNonNull(kbId));
        if (!canEditKb(userId, kb)) {
            throw new BusinessException(ApiResponse.Code.NO_PERMISSION);
        }
        if (StringUtils.hasText(dto.getTitle())) {
            kb.setTitle(dto.getTitle());
        }
        kb.setDescription(dto.getDescription());
        if (StringUtils.hasText(dto.getColor())) {
            kb.setColor(dto.getColor());
        }
        if (StringUtils.hasText(dto.getIcon())) {
            kb.setIcon(dto.getIcon());
        }
        if (dto.getAllowAnonymous() != null) {
            kb.setAllowAnonymous(dto.getAllowAnonymous());
        }
        if (dto.getVisibility() != null) {
            kb.setVisibility(dto.getVisibility());
        }
        kb.setUpdatedAt(OffsetDateTime.now());
        return kbRepository.save(kb);
    }

    @Transactional
    @Auditable(targetType = AuditTargetType.KB, action = AuditAction.DELETE, targetIdExpression = "#kbId")
    public void softDeleteKnowledgeBase(Long userId, Long kbId) {
        KbKnowledgeBase kb = getKnowledgeBase(Objects.requireNonNull(kbId));
        if (!canEditKb(userId, kb)) {
            throw new BusinessException(ApiResponse.Code.NO_PERMISSION);
        }
        kb.setDeletedAt(OffsetDateTime.now());
        kbRepository.save(kb);
    }

    @Transactional
    @Auditable(targetType = AuditTargetType.KB, action = AuditAction.RESTORE, targetIdExpression = "#kbId")
    public void restoreKnowledgeBase(Long userId, Long kbId) {
        KbKnowledgeBase kb = kbRepository.findById(kbId)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeBase", kbId));
        if (!Objects.equals(kb.getOwnerId(), userId)) {
            throw new BusinessException(ApiResponse.Code.NO_PERMISSION);
        }
        kb.setDeletedAt(null);
        kbRepository.save(kb);
    }

    @Transactional
    @Auditable(targetType = AuditTargetType.KB, action = AuditAction.DELETE, targetIdExpression = "#kbId")
    public void permanentDeleteKnowledgeBase(Long userId, Long kbId) {
        KbKnowledgeBase kb = kbRepository.findById(kbId)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeBase", kbId));
        if (!Objects.equals(kb.getOwnerId(), userId)) {
            throw new BusinessException(ApiResponse.Code.NO_PERMISSION);
        }
        if (kb.getDeletedAt() == null) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, "Only deleted items can be permanently deleted");
        }
        kbRepository.delete(kb);
    }

    @Transactional
    public KbKbUserPref updatePin(Long userId, Long kbId, boolean pinned) {
        KbKnowledgeBase kb = getKnowledgeBase(Objects.requireNonNull(kbId));
        if (!canReadKb(userId, kb)) {
            throw new BusinessException(ApiResponse.Code.NO_PERMISSION);
        }
        KbKbUserPref pref = prefRepository.findByUserIdAndKbId(userId, kbId)
                .orElseGet(() -> {
                    KbKbUserPref created = new KbKbUserPref();
                    created.setUserId(userId);
                    created.setKb(kb);
                    created.setCreatedAt(OffsetDateTime.now());
                    return created;
                });
        pref.setIsPinned(pinned);
        pref.setPinnedAt(pinned ? OffsetDateTime.now() : null);
        pref.setUpdatedAt(OffsetDateTime.now());
        return prefRepository.save(pref);
    }

    @Transactional
    public void updateSortOrders(Long userId, List<Long> kbIds) {
        for (int i = 0; i < kbIds.size(); i++) {
            Long kbId = kbIds.get(i);
            KbKbUserPref pref = prefRepository.findByUserIdAndKbId(userId, kbId)
                    .orElseGet(() -> {
                        KbKbUserPref created = new KbKbUserPref();
                        created.setUserId(userId);
                        created.setKb(getKnowledgeBase(Objects.requireNonNull(kbId)));
                        created.setCreatedAt(OffsetDateTime.now());
                        return created;
                    });
            pref.setSortOrder(i);
            pref.setUpdatedAt(OffsetDateTime.now());
            prefRepository.save(pref);
        }
    }

    public Map<Long, SysUser> loadUsersByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, user -> user));
    }

    public Optional<SysUser> findUserByUsername(String username) {
        return userService.getUserByUsername(username);
    }

    public KbRole getRole(Long userId, Long kbId) {
        return memberRepository.findByKbIdAndUserId(kbId, userId)
                .map(KbKbMember::getRole)
                .orElse(null);
    }
}
