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
@Tag(name = "Permission Management", description = "Permission CRUD and role permission assignment APIs")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Permission list", description = "Get list of all permissions")
    public ApiResponse<List<SysPermission>> listPermissions() {
        return ApiResponse.success(permissionService.listPermissions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Permission details", description = "Get permission by ID")
    public ApiResponse<SysPermission> getPermission(@PathVariable Long id) {
        return ApiResponse.success(permissionService.getPermission(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Create permission", description = "Create new permission")
    public ApiResponse<SysPermission> createPermission(@RequestBody PermissionRequest request) {
        return ApiResponse.success(permissionService.createPermission(request.permCode(), request.permName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Delete permission", description = "Delete permission")
    public ApiResponse<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ApiResponse.success(null);
    }

    // ==================== Role Permission Assignment ====================

    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Role permission list", description = "Get all permissions assigned to role")
    public ApiResponse<List<SysPermission>> getRolePermissions(@PathVariable Long roleId) {
        return ApiResponse.success(permissionService.getRolePermissions(roleId));
    }

    @PostMapping("/roles/{roleId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Grant permission", description = "Grant permission to role")
    public ApiResponse<Void> grantPermission(@PathVariable Long roleId, @RequestBody GrantPermissionRequest request) {
        permissionService.grantPermission(roleId, request.permId());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Revoke permission", description = "Revoke permission from role")
    public ApiResponse<Void> revokePermission(@PathVariable Long roleId, @PathVariable Long permId) {
        permissionService.revokePermission(roleId, permId);
        return ApiResponse.success(null);
    }

    // ==================== User Permission Query ====================

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('admin') or #userId == authentication.principal.id")
    @Operation(summary = "User permission list", description = "Get all permission codes assigned to user")
    public ApiResponse<Set<String>> getUserPermissions(@PathVariable Long userId) {
        return ApiResponse.success(permissionService.getUserPermissions(userId));
    }

    // ==================== DTO ====================

    public record PermissionRequest(String permCode, String permName) {
    }

    public record GrantPermissionRequest(Long permId) {
    }
}
