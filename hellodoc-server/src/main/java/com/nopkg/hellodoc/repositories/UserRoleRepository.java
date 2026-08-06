package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.SysUserRole;
import com.nopkg.hellodoc.entities.SysUserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserRoleRepository extends JpaRepository<SysUserRole, SysUserRoleId> {
    List<SysUserRole> findByIdUserId(Long userId);
    List<SysUserRole> findByIdUserIdIn(List<Long> userIds);
    List<SysUserRole> findByIdRoleId(Long roleId);
    void deleteByIdUserId(Long userId);
}
