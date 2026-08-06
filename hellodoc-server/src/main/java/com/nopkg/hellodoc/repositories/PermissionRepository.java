package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PermissionRepository extends JpaRepository<SysPermission, Long> {

    Optional<SysPermission> findByPermCode(String permCode);

    List<SysPermission> findByPermCodeIn(List<String> permCodes);

    /**
     * 根据角色ID列表获取权限编码集合
     */
    @Query(value = "SELECT DISTINCT p.perm_code FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON rp.perm_id = p.id " +
            "WHERE rp.role_id IN (:roleIds)", nativeQuery = true)
    Set<String> findPermCodesByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 根据角色ID获取权限列表
     */
    @Query(value = "SELECT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON rp.perm_id = p.id " +
            "WHERE rp.role_id = :roleId", nativeQuery = true)
    List<SysPermission> findByRoleId(@Param("roleId") Long roleId);
}
