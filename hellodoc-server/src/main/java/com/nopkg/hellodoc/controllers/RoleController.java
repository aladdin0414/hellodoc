package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.SysRole;
import com.nopkg.hellodoc.services.RoleService;
import com.nopkg.hellodoc.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器
 */
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色 CRUD 及用户角色分配")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "角色列表", description = "获取所有角色")
    public ApiResponse<List<SysRole>> listRoles() {
        return ApiResponse.success(roleService.listRoles());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "角色详情", description = "根据ID获取角色")
    public ApiResponse<SysRole> getRole(@PathVariable Long id) {
        return roleService.getRole(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(ApiResponse.Code.RESOURCE_NOT_FOUND));
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "创建角色", description = "创建新角色")
    public ApiResponse<SysRole> createRole(@RequestBody RoleRequest request) {
        SysRole role = new SysRole();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setStatus(request.status() != null ? request.status() : (short) 0);
        return ApiResponse.success(roleService.createRole(role));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "更新角色", description = "更新角色信息")
    public ApiResponse<SysRole> updateRole(@PathVariable Long id, @RequestBody RoleRequest request) {
        SysRole roleDetails = new SysRole();
        roleDetails.setRoleCode(request.roleCode());
        roleDetails.setRoleName(request.roleName());
        roleDetails.setStatus(request.status());
        return ApiResponse.success(roleService.updateRole(id, roleDetails));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "删除角色", description = "删除角色")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ApiResponse.success(null);
    }

    // ==================== 用户角色分配 ====================

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "用户角色列表", description = "获取用户的所有角色")
    public ApiResponse<List<SysRole>> getUserRoles(@PathVariable Long userId) {
        return ApiResponse.success(roleService.getUserRoles(userId));
    }

    @PostMapping("/users/{userId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "分配角色", description = "给用户分配角色")
    public ApiResponse<Void> assignRole(@PathVariable Long userId, @RequestBody AssignRoleRequest request) {
        roleService.assignRole(userId, request.roleId());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "撤销角色", description = "撤销用户的角色")
    public ApiResponse<Void> revokeRole(@PathVariable Long userId, @PathVariable Long roleId) {
        roleService.revokeRole(userId, roleId);
        return ApiResponse.success(null);
    }

    // ==================== DTO ====================

    public record RoleRequest(String roleCode, String roleName, Short status) {
    }

    public record AssignRoleRequest(Long roleId) {
    }
}
