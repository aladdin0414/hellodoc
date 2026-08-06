package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.KbKbMember;
import com.nopkg.hellodoc.entities.KbKbUserPref;
import com.nopkg.hellodoc.entities.KbKnowledgeBase;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.enums.KbRole;
import com.nopkg.hellodoc.enums.Visibility;
import com.nopkg.hellodoc.security.RequireKbRole;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.web.dto.kb.KbCreateDTO;
import com.nopkg.hellodoc.web.dto.kb.KbUpdateDTO;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.exceptions.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
@Tag(name = "知识库管理", description = "知识库与文档管理接口")
public class KbController {

        private final KbService kbService;

        record KbSummary(Long id, String title, String description, String icon, String color,
                        Long ownerId, String ownerName, String ownerAvatar, Boolean isPinned, Boolean isShared,
                        String lastModified,
                        Boolean allowAnonymous, Visibility visibility, OffsetDateTime pinnedAt, Integer sortOrder,
                        OffsetDateTime createdAt, String role) {
        }

        record CreateKbRequest(String title, String description, String color, String icon,
                        Boolean allowAnonymous, Visibility visibility) {
        }

        record UpdateKbRequest(String title, String description, String color, String icon,
                        Boolean allowAnonymous, Visibility visibility) {
        }

        record PinRequest(Boolean pinned) {
        }

        record ReorderKbsRequest(List<Long> kbIds) {
        }

        private Long currentUserId() {
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                return kbService.requireUserId(username);
        }

        @GetMapping("/listKnowledgeBases")
        @Operation(summary = "获取知识库列表", description = "获取当前用户可访问的知识库列表")
        public ApiResponse<List<KbSummary>> listKnowledgeBases() {
                Long userId = currentUserId();
                List<KbKnowledgeBase> bases = kbService.listAccessibleKnowledgeBases(userId);
                Map<Long, KbKbUserPref> prefs = kbService.listUserPrefs(userId);
                Map<Long, List<KbKbMember>> membersByKb = kbService.listMembersByKbIds(
                                bases.stream().map(KbKnowledgeBase::getId).toList());
                Set<Long> ownerIds = bases.stream()
                                .map(KbKnowledgeBase::getOwnerId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());
                Map<Long, SysUser> owners = kbService.loadUsersByIds(ownerIds);
                List<KbSummary> summaries = bases.stream()
                                .map(kb -> {
                                        KbKbUserPref pref = prefs.get(kb.getId());
                                        boolean pinned = pref != null && Boolean.TRUE.equals(pref.getIsPinned());
                                        List<KbKbMember> members = membersByKb.getOrDefault(kb.getId(), List.of());
                                        boolean shared = members.stream()
                                                        .anyMatch(m -> !Objects.equals(m.getUserId(), kb.getOwnerId()));
                                        SysUser owner = owners.get(kb.getOwnerId());
                                        String ownerName = owner != null ? owner.getNickname() : "";
                                        String ownerAvatar = owner != null ? owner.getAvatar() : "";
                                        String lastModified = Optional.ofNullable(kb.getUpdatedAt())
                                                        .map(OffsetDateTime::toString).orElse("");

                                        String role = "VIEWER";
                                        if (Objects.equals(userId, kb.getOwnerId())) {
                                                role = "OWNER";
                                        } else {
                                                role = members.stream()
                                                                .filter(m -> Objects.equals(m.getUserId(), userId))
                                                                .map(m -> m.getRole().name())
                                                                .findFirst()
                                                                .orElse("VIEWER");
                                        }

                                        return new KbSummary(kb.getId(), kb.getTitle(), kb.getDescription(),
                                                        kb.getIcon(), kb.getColor(),
                                                        kb.getOwnerId(), ownerName, ownerAvatar, pinned, shared,
                                                        lastModified,
                                                        kb.getAllowAnonymous(), kb.getVisibility(),
                                                        pref != null ? pref.getPinnedAt() : null,
                                                        pref != null ? pref.getSortOrder() : null,
                                                        kb.getCreatedAt(), role);
                                })
                                .sorted((s1, s2) -> {
                                        // 1. 置顶的知识库 (按 pinnedAt 倒序排列)
                                        if (Boolean.TRUE.equals(s1.isPinned()) && Boolean.TRUE.equals(s2.isPinned())) {
                                                if (s1.pinnedAt() != null && s2.pinnedAt() != null) {
                                                        return s2.pinnedAt().compareTo(s1.pinnedAt());
                                                }
                                                return 0;
                                        }
                                        if (Boolean.TRUE.equals(s1.isPinned()))
                                                return -1;
                                        if (Boolean.TRUE.equals(s2.isPinned()))
                                                return 1;

                                        // 2. 未置顶的知识库 (按 sortOrder 升序排列，然后按 createdAt 倒序排列)
                                        int order1 = s1.sortOrder() != null ? s1.sortOrder() : Integer.MAX_VALUE;
                                        int order2 = s2.sortOrder() != null ? s2.sortOrder() : Integer.MAX_VALUE;
                                        if (order1 != order2) {
                                                return Integer.compare(order1, order2);
                                        }

                                        if (s1.createdAt() != null && s2.createdAt() != null) {
                                                return s2.createdAt().compareTo(s1.createdAt());
                                        }
                                        return 0;
                                })
                                .toList();
                return ApiResponse.success(summaries);
        }

        @PostMapping("/createKnowledgeBase")
        @Operation(summary = "创建知识库", description = "创建新的知识库")
        public ApiResponse<KbSummary> createKnowledgeBase(@RequestBody CreateKbRequest request) {
                Long userId = currentUserId();
                KbCreateDTO dto = new KbCreateDTO();
                dto.setTitle(request.title());
                dto.setDescription(request.description());
                dto.setColor(request.color());
                dto.setIcon(request.icon());
                dto.setAllowAnonymous(request.allowAnonymous());
                dto.setVisibility(request.visibility());

                KbKnowledgeBase kb = kbService.createKnowledgeBase(userId, dto);
                SysUser owner = kbService.loadUsersByIds(Set.of(userId)).get(userId);
                String ownerName = owner != null ? owner.getNickname() : "";
                String ownerAvatar = owner != null ? owner.getAvatar() : "";
                KbSummary summary = new KbSummary(kb.getId(), kb.getTitle(), kb.getDescription(), kb.getIcon(),
                                kb.getColor(), kb.getOwnerId(), ownerName, ownerAvatar, false, false,
                                Optional.ofNullable(kb.getUpdatedAt()).map(OffsetDateTime::toString).orElse(""),
                                kb.getAllowAnonymous(), kb.getVisibility(), null, null, kb.getCreatedAt(), "OWNER");
                return ApiResponse.success(summary);
        }

        @GetMapping("/{kbId}")
        @Operation(summary = "获取知识库详情", description = "获取指定知识库的详细信息")
        @RequireKbRole(KbRole.VIEWER)
        public ApiResponse<KbSummary> getKnowledgeBase(@PathVariable Long kbId) {
                Long userId = currentUserId();
                KbKnowledgeBase kb = kbService.getKnowledgeBase(Objects.requireNonNull(kbId));
                if (!kbService.canReadKb(userId, kb)) {
                        throw new BusinessException(ApiResponse.Code.NO_PERMISSION);
                }

                SysUser owner = kbService.loadUsersByIds(Set.of(kb.getOwnerId())).get(kb.getOwnerId());
                String ownerName = owner != null ? owner.getNickname() : "";
                String ownerAvatar = owner != null ? owner.getAvatar() : "";
                boolean shared = kbService.isShared(kbId);
                Map<Long, KbKbUserPref> prefs = kbService.listUserPrefs(userId);
                KbKbUserPref pref = prefs.get(kbId);

                String role = "VIEWER";
                if (Objects.equals(userId, kb.getOwnerId())) {
                        role = "OWNER";
                } else {
                        KbRole kbRole = kbService.getRole(userId, kbId);
                        role = kbRole != null ? kbRole.name() : "VIEWER";
                }

                KbSummary summary = new KbSummary(kb.getId(), kb.getTitle(), kb.getDescription(), kb.getIcon(),
                                kb.getColor(), kb.getOwnerId(), ownerName, ownerAvatar,
                                pref != null && Boolean.TRUE.equals(pref.getIsPinned()), shared,
                                Optional.ofNullable(kb.getUpdatedAt()).map(OffsetDateTime::toString).orElse(""),
                                kb.getAllowAnonymous(), kb.getVisibility(),
                                pref != null ? pref.getPinnedAt() : null,
                                pref != null ? pref.getSortOrder() : null,
                                kb.getCreatedAt(), role);
                return ApiResponse.success(summary);
        }

        @PutMapping("/{kbId}")
        @Operation(summary = "更新知识库", description = "更新知识库信息")
        @RequireKbRole(KbRole.ADMIN)
        public ApiResponse<KbSummary> updateKnowledgeBase(@PathVariable Long kbId,
                        @RequestBody UpdateKbRequest request) {
                Long userId = currentUserId();
                KbUpdateDTO dto = new KbUpdateDTO();
                dto.setTitle(request.title());
                dto.setDescription(request.description());
                dto.setColor(request.color());
                dto.setIcon(request.icon());
                dto.setAllowAnonymous(request.allowAnonymous());
                dto.setVisibility(request.visibility());

                KbKnowledgeBase kb = kbService.updateKnowledgeBase(userId, kbId, dto);
                SysUser owner = kbService.loadUsersByIds(Set.of(kb.getOwnerId())).get(kb.getOwnerId());
                String ownerName = owner != null ? owner.getNickname() : "";
                String ownerAvatar = owner != null ? owner.getAvatar() : "";
                boolean shared = kbService.isShared(kbId);

                String role = "VIEWER";
                if (Objects.equals(userId, kb.getOwnerId())) {
                        role = "OWNER";
                } else {
                        KbRole kbRole = kbService.getRole(userId, kbId);
                        role = kbRole != null ? kbRole.name() : "VIEWER";
                }

                KbSummary summary = new KbSummary(kb.getId(), kb.getTitle(), kb.getDescription(), kb.getIcon(),
                                kb.getColor(), kb.getOwnerId(), ownerName, ownerAvatar, false, shared,
                                Optional.ofNullable(kb.getUpdatedAt()).map(OffsetDateTime::toString).orElse(""),
                                kb.getAllowAnonymous(), kb.getVisibility(), null, null, kb.getCreatedAt(), role);
                return ApiResponse.success(summary);
        }

        @DeleteMapping("/{kbId}")
        @Operation(summary = "删除知识库", description = "删除指定知识库")
        @RequireKbRole(KbRole.ADMIN)
        public ApiResponse<Void> deleteKnowledgeBase(@PathVariable Long kbId) {
                Long userId = currentUserId();
                kbService.softDeleteKnowledgeBase(userId, kbId);
                return ApiResponse.success(null);
        }

        @PostMapping("/{kbId}/restore")
        @Operation(summary = "恢复知识库", description = "恢复被软删除的知识库")
        @RequireKbRole(KbRole.OWNER)
        public ApiResponse<Void> restoreKnowledgeBase(@PathVariable Long kbId) {
                Long userId = currentUserId();
                kbService.restoreKnowledgeBase(userId, kbId);
                return ApiResponse.success(null);
        }

        @GetMapping("/trash")
        @Operation(summary = "回收站列表", description = "获取我的回收站列表")
        public ApiResponse<List<KbSummary>> listTrash() {
                Long userId = currentUserId();
                List<KbKnowledgeBase> bases = kbService.listTrash(userId);
                List<KbSummary> summaries = bases.stream()
                                .map(kb -> {
                                        String lastModified = Optional.ofNullable(kb.getUpdatedAt())
                                                        .map(OffsetDateTime::toString).orElse("");
                                        return new KbSummary(kb.getId(), kb.getTitle(), kb.getDescription(),
                                                        kb.getIcon(), kb.getColor(),
                                                        kb.getOwnerId(), "", "", false, false, lastModified,
                                                        kb.getAllowAnonymous(), kb.getVisibility(), null, null,
                                                        kb.getCreatedAt(), "OWNER");
                                })
                                .toList();
                return ApiResponse.success(summaries);
        }

        @PostMapping("/{kbId}/pin")
        @Operation(summary = "置顶知识库", description = "设置知识库置顶状态")
        @RequireKbRole(KbRole.VIEWER)
        public ApiResponse<Boolean> pinKnowledgeBase(@PathVariable Long kbId, @RequestBody PinRequest request) {
                Long userId = currentUserId();
                boolean pinned = request != null && Boolean.TRUE.equals(request.pinned());
                KbKbUserPref pref = kbService.updatePin(userId, kbId, pinned);
                return ApiResponse.success(Boolean.TRUE.equals(pref.getIsPinned()));
        }

        @PostMapping("/reorder")
        @Operation(summary = "重排知识库", description = "设置我的知识库排序顺序")
        public ApiResponse<Void> reorderKnowledgeBases(@RequestBody ReorderKbsRequest request) {
                Long userId = currentUserId();
                kbService.updateSortOrders(userId, request.kbIds());
                return ApiResponse.success(null);
        }

}
