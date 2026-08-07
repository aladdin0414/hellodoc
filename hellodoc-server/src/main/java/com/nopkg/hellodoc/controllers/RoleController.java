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
@Tag(name = "Role Management", description = "Role CRUD and user role assignment APIs")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Role list", description = "Get all roles")
    public ApiResponse<List<SysRole>> listRoles() {
        return ApiResponse.success(roleService.listRoles());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Role details", description = "Get role by ID")
    public ApiResponse<SysRole> getRole(@PathVariable Long id) {
        return roleService.getRole(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(ApiResponse.Code.RESOURCE_NOT_FOUND));
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Create role", description = "Create new role")
    public ApiResponse<SysRole> createRole(@RequestBody RoleRequest request) {
        SysRole role = new SysRole();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setStatus(request.status() != null ? request.status() : (short) 0);
        return ApiResponse.success(roleService.createRole(role));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Update role", description = "Update role information")
    public ApiResponse<SysRole> updateRole(@PathVariable Long id, @RequestBody RoleRequest request) {
        SysRole roleDetails = new SysRole();
        roleDetails.setRoleCode(request.roleCode());
        roleDetails.setRoleName(request.roleName());
        roleDetails.setStatus(request.status());
        return ApiResponse.success(roleService.updateRole(id, roleDetails));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Delete role", description = "Delete role")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ApiResponse.success(null);
    }

    // ==================== User Role Assignment ====================

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "User role list", description = "Get all roles assigned to user")
    public ApiResponse<List<SysRole>> getUserRoles(@PathVariable Long userId) {
        return ApiResponse.success(roleService.getUserRoles(userId));
    }

    @PostMapping("/users/{userId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Assign role", description = "Assign role to user")
    public ApiResponse<Void> assignRole(@PathVariable Long userId, @RequestBody AssignRoleRequest request) {
        roleService.assignRole(userId, request.roleId());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Revoke role", description = "Revoke role from user")
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
