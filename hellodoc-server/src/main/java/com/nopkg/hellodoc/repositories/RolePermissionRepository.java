package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.SysRolePermission;
import com.nopkg.hellodoc.entities.SysRolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<SysRolePermission, SysRolePermissionId> {

    List<SysRolePermission> findByIdRoleId(Long roleId);

    List<SysRolePermission> findByIdPermId(Long permId);

    void deleteByIdRoleId(Long roleId);

    void deleteByIdPermId(Long permId);
}
