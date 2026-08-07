package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.SysPermission;
import com.nopkg.hellodoc.entities.SysRole;
import com.nopkg.hellodoc.entities.SysRolePermission;
import com.nopkg.hellodoc.entities.SysRolePermissionId;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.repositories.PermissionRepository;
import com.nopkg.hellodoc.repositories.RolePermissionRepository;
import com.nopkg.hellodoc.repositories.RoleRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 权限服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;

    // ==================== 权限 CRUD ====================

    public List<SysPermission> listPermissions() {
        return permissionRepository.findAll();
    }

    public SysPermission getPermission(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("permission.not_found", "Permission not found")));
    }

    @Transactional
    public SysPermission createPermission(String permCode, String permName) {
        if (permissionRepository.findByPermCode(permCode).isPresent()) {
            throw new BusinessException(ApiResponse.Code.USERNAME_CONFLICT, com.nopkg.hellodoc.utils.MessageUtils.get("permission.code_exists", "Permission code already exists"));
        }

        SysPermission permission = new SysPermission();
        permission.setPermCode(permCode);
        permission.setPermName(permName);
        permission.setCreateTime(Instant.now());

        return permissionRepository.save(permission);
    }

    @Transactional
    public void deletePermission(Long id) {
        // 先删除角色权限关联
        rolePermissionRepository.deleteByIdPermId(id);
        permissionRepository.deleteById(id);
    }

    // ==================== 角色权限关联 ====================

    /**
     * 授予角色权限
     */
    @Transactional
    public void grantPermission(Long roleId, Long permId) {
        // 验证角色和权限存在
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("role.not_found", "Role not found")));
        SysPermission perm = permissionRepository.findById(permId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("permission.not_found", "Permission not found")));

        SysRolePermissionId id = new SysRolePermissionId();
        id.setRoleId(roleId);
        id.setPermId(permId);

        if (!rolePermissionRepository.existsById(id)) {
            SysRolePermission rolePermission = new SysRolePermission();
            rolePermission.setId(id);
            rolePermission.setRole(role);
            rolePermission.setPerm(perm);
            rolePermissionRepository.save(rolePermission);
            log.info("Grant permission {} to role {}", permId, roleId);
        }
    }

    /**
     * 撤销角色权限
     */
    @Transactional
    public void revokePermission(Long roleId, Long permId) {
        SysRolePermissionId id = new SysRolePermissionId();
        id.setRoleId(roleId);
        id.setPermId(permId);
        rolePermissionRepository.deleteById(id);
        log.info("Revoke permission {} from role {}", permId, roleId);
    }

    /**
     * 获取角色的权限列表
     */
    public List<SysPermission> getRolePermissions(Long roleId) {
        return permissionRepository.findByRoleId(roleId);
    }

    /**
     * 获取用户的所有权限编码（通过角色）
     */
    public Set<String> getUserPermissions(Long userId) {
        // 获取用户的所有角色ID
        List<SysRole> roles = roleRepository.findRolesByUserId(userId);
        if (roles.isEmpty()) {
            return Collections.emptySet();
        }

        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();

        // 检查是否为管理员角色
        boolean isAdmin = roles.stream()
                .anyMatch(r -> "admin".equals(r.getRoleCode()));

        if (isAdmin) {
            // 管理员拥有所有权限
            return permissionRepository.findAll().stream()
                    .map(SysPermission::getPermCode)
                    .collect(java.util.stream.Collectors.toSet());
        }

        return permissionRepository.findPermCodesByRoleIds(roleIds);
    }

    /**
     * 检查用户是否拥有指定权限
     */
    public boolean hasPermission(Long userId, String permCode) {
        Set<String> permissions = getUserPermissions(userId);
        return permissions.contains(permCode);
    }

    /**
     * 检查用户是否拥有任一指定权限
     */
    public boolean hasAnyPermission(Long userId, String... permCodes) {
        Set<String> permissions = getUserPermissions(userId);
        for (String code : permCodes) {
            if (permissions.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查用户是否拥有所有指定权限
     */
    public boolean hasAllPermissions(Long userId, String... permCodes) {
        Set<String> permissions = getUserPermissions(userId);
        for (String code : permCodes) {
            if (!permissions.contains(code)) {
                return false;
            }
        }
        return true;
    }
}
