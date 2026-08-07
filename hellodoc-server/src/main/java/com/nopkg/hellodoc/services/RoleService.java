package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.SysRole;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserRole;
import com.nopkg.hellodoc.entities.SysUserRoleId;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.repositories.RoleRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.repositories.UserRoleRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    // ==================== 角色 CRUD ====================

    public List<SysRole> listRoles() {
        return roleRepository.findAll();
    }

    public Optional<SysRole> getRole(Long id) {
        return roleRepository.findById(id);
    }

    public Optional<SysRole> getRoleByCode(String code) {
        return roleRepository.findByRoleCode(code);
    }

    @Transactional
    public SysRole createRole(SysRole role) {
        if (role.getRoleCode() != null && "SUPER_ADMIN".equalsIgnoreCase(role.getRoleCode())) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("role.super_admin_not_allowed", "Creating SUPER_ADMIN role is not allowed, please use admin"));
        }
        role.setCreateTime(Instant.now());
        return roleRepository.save(role);
    }

    @Transactional
    public SysRole updateRole(Long id, SysRole roleDetails) {
        return roleRepository.findById(id).map(role -> {
            role.setRoleName(roleDetails.getRoleName());
            if (roleDetails.getRoleCode() != null && "SUPER_ADMIN".equalsIgnoreCase(roleDetails.getRoleCode())) {
                throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("role.super_admin_not_allowed", "Creating SUPER_ADMIN role is not allowed, please use admin"));
            }
            role.setRoleCode(roleDetails.getRoleCode());
            role.setStatus(roleDetails.getStatus());
            return roleRepository.save(role);
        }).orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("role.not_found", "Role not found")));
    }

    @Transactional
    public void deleteRole(Long id) {
        roleRepository.deleteById(id);
    }

    // ==================== 用户角色分配 ====================

    /**
     * 分配角色给用户
     */
    @Transactional
    public void assignRole(Long userId, Long roleId) {
        // 验证角色和用户存在
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("role.not_found", "Role not found")));
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("user.not_found", "User not found")));

        SysUserRoleId id = new SysUserRoleId();
        id.setUserId(userId);
        id.setRoleId(roleId);

        if (!userRoleRepository.existsById(id)) {
            SysUserRole userRole = new SysUserRole();
            userRole.setId(id);
            userRole.setUser(user); // 设置关联实体
            userRole.setRole(role); // 设置关联实体
            userRoleRepository.save(userRole);
            log.info("Assign role {} to user {}", roleId, userId);
        }
    }

    /**
     * 撤销用户角色
     */
    @Transactional
    public void revokeRole(Long userId, Long roleId) {
        SysUserRoleId id = new SysUserRoleId();
        id.setUserId(userId);
        id.setRoleId(roleId);
        userRoleRepository.deleteById(id);
        log.info("Revoke role {} from user {}", roleId, userId);
    }

    /**
     * 获取用户的角色列表
     */
    public List<SysRole> getUserRoles(Long userId) {
        return roleRepository.findRolesByUserId(userId);
    }
}
