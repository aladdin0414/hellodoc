package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<SysRole, Long> {
    Optional<SysRole> findByRoleName(String roleName);

    Optional<SysRole> findByRoleCode(String roleCode);

    @Query(value = "SELECT r.* FROM sys_role r LEFT JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = :userId", nativeQuery = true)
    List<SysRole> findRolesByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT r.id FROM sys_role r LEFT JOIN sys_role_permission rp ON rp.role_id = r.id WHERE rp.perm_id = :permId", nativeQuery = true)
    List<Long> findRoleIdsByPermId(@Param("permId") Long permId);

    @Query(value = "SELECT r.role_code FROM sys_role r LEFT JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = :userId", nativeQuery = true)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}