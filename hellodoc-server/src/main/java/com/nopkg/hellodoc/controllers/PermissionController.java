package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.SysPermission;
import com.nopkg.hellodoc.services.PermissionService;
import com.nopkg.hellodoc.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 权限管理控制器
 */
@RestController
@RequestMapping("/api/system/permissions")
@RequiredArgsConstructor
@Tag(name = "权限管理", description = "权限 CRUD 及角色权限分配")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "权限列表", description = "获取所有权限")
    public ApiResponse<List<SysPermission>> listPermissions() {
        return ApiResponse.success(permissionService.listPermissions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "权限详情", description = "根据ID获取权限")
    public ApiResponse<SysPermission> getPermission(@PathVariable Long id) {
        return ApiResponse.success(permissionService.getPermission(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "创建权限", description = "创建新权限")
    public ApiResponse<SysPermission> createPermission(@RequestBody PermissionRequest request) {
        return ApiResponse.success(permissionService.createPermission(request.permCode(), request.permName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "删除权限", description = "删除权限")
    public ApiResponse<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ApiResponse.success(null);
    }

    // ==================== 角色权限分配 ====================

    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "角色权限列表", description = "获取角色的所有权限")
    public ApiResponse<List<SysPermission>> getRolePermissions(@PathVariable Long roleId) {
        return ApiResponse.success(permissionService.getRolePermissions(roleId));
    }

    @PostMapping("/roles/{roleId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "授予权限", description = "给角色授予权限")
    public ApiResponse<Void> grantPermission(@PathVariable Long roleId, @RequestBody GrantPermissionRequest request) {
        permissionService.grantPermission(roleId, request.permId());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "撤销权限", description = "撤销角色的权限")
    public ApiResponse<Void> revokePermission(@PathVariable Long roleId, @PathVariable Long permId) {
        permissionService.revokePermission(roleId, permId);
        return ApiResponse.success(null);
    }

    // ==================== 用户权限查询 ====================

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('admin') or #userId == authentication.principal.id")
    @Operation(summary = "用户权限列表", description = "获取用户的所有权限编码")
    public ApiResponse<Set<String>> getUserPermissions(@PathVariable Long userId) {
        return ApiResponse.success(permissionService.getUserPermissions(userId));
    }

    // ==================== DTO ====================

    public record PermissionRequest(String permCode, String permName) {
    }

    public record GrantPermissionRequest(Long permId) {
    }
}
