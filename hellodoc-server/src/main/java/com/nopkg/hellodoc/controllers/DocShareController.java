package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.KbDocPermission;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.enums.DocRole;
import com.nopkg.hellodoc.enums.TargetType;
import com.nopkg.hellodoc.security.RequireDocRole;
import com.nopkg.hellodoc.services.KbDocPermissionService;
import com.nopkg.hellodoc.services.KbService;
import com.nopkg.hellodoc.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/docs/{docId}/share")
@RequiredArgsConstructor
@Tag(name = "文档分享管理", description = "文档权限及分享链接管理接口")
public class DocShareController {

    private final KbDocPermissionService permissionService;
    private final KbService kbService;

    record DocPermissionVO(Long id, TargetType targetType, Long targetId, String targetName,
            String targetAvatar, String linkToken, DocRole role, String expiresAt) {
    }

    record AddPermissionRequest(TargetType targetType, Long targetId, String username,
            DocRole role, OffsetDateTime expiresAt) {
    }

    record CreateLinkRequest(DocRole role, OffsetDateTime expiresAt) {
    }

    @GetMapping
    @Operation(summary = "分享列表", description = "获取文档的所有分享及权限设置")
    @RequireDocRole(DocRole.VIEWER)
    public ApiResponse<List<DocPermissionVO>> listPermissions(@PathVariable Long docId) {
        List<KbDocPermission> permissions = permissionService.listPermissions(docId);

        Set<Long> userIds = permissions.stream()
                .filter(p -> p.getTargetType() == TargetType.USER)
                .map(KbDocPermission::getTargetId)
                .collect(Collectors.toSet());

        Map<Long, SysUser> users = kbService.loadUsersByIds(userIds);

        List<DocPermissionVO> result = permissions.stream()
                .map(p -> {
                    String name = "";
                    String avatar = "";
                    if (p.getTargetType() == TargetType.USER) {
                        SysUser user = users.get(p.getTargetId());
                        if (user != null) {
                            name = user.getNickname();
                            avatar = user.getAvatar();
                        }
                    } else if (p.getTargetType() == TargetType.LINK) {
                        name = "公开链接";
                    }

                    return new DocPermissionVO(p.getId(), p.getTargetType(), p.getTargetId(),
                            name, avatar, p.getLinkToken(), p.getRole(),
                            p.getExpiresAt() != null ? p.getExpiresAt().toString() : null);
                })
                .toList();

        return ApiResponse.success(result);
    }

    @PostMapping
    @Operation(summary = "添加权限", description = "为用户或分组添加文档权限")
    @RequireDocRole(DocRole.EDITOR)
    public ApiResponse<DocPermissionVO> addPermission(@PathVariable Long docId,
            @RequestBody AddPermissionRequest request) {
        Long targetId = request.targetId();
        if (targetId == null && request.username() != null) {
            targetId = kbService.findUserByUsername(request.username())
                    .map(SysUser::getId)
                    .orElseThrow(() -> new com.nopkg.hellodoc.exceptions.ResourceNotFoundException("User",
                            request.username()));
        }

        KbDocPermission p = permissionService.addPermission(docId, request.targetType(), targetId, request.role(),
                request.expiresAt());

        String name = "";
        String avatar = "";
        if (p.getTargetType() == TargetType.USER) {
            SysUser user = kbService.loadUsersByIds(Set.of(p.getTargetId())).get(p.getTargetId());
            if (user != null) {
                name = user.getNickname();
                avatar = user.getAvatar();
            }
        }

        DocPermissionVO response = new DocPermissionVO(p.getId(), p.getTargetType(), p.getTargetId(),
                name, avatar, p.getLinkToken(), p.getRole(),
                p.getExpiresAt() != null ? p.getExpiresAt().toString() : null);

        return ApiResponse.success(response);
    }

    @PostMapping("/link")
    @Operation(summary = "创建分享链接", description = "创建文档公开分享链接")
    @RequireDocRole(DocRole.EDITOR)
    public ApiResponse<DocPermissionVO> createLink(@PathVariable Long docId, @RequestBody CreateLinkRequest request) {
        KbDocPermission p = permissionService.createShareLink(docId, request.role(), request.expiresAt());

        DocPermissionVO response = new DocPermissionVO(p.getId(), p.getTargetType(), null,
                "公开链接", "", p.getLinkToken(), p.getRole(),
                p.getExpiresAt() != null ? p.getExpiresAt().toString() : null);

        return ApiResponse.success(response);
    }

    @DeleteMapping("/{permissionId}")
    @Operation(summary = "取消分享", description = "删除指定的权限或分享链接")
    @RequireDocRole(DocRole.EDITOR)
    public ApiResponse<Void> removePermission(@PathVariable Long docId, @PathVariable Long permissionId) {
        permissionService.removePermission(permissionId);
        return ApiResponse.success(null);
    }
}
