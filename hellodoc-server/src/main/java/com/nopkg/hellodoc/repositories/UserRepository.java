package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<SysUser, Long>, JpaSpecificationExecutor<SysUser> {

        Optional<SysUser> findByEmail(String email);

        Optional<SysUser> findByPhone(String phone);

        boolean existsByEmail(String email);

        boolean existsByPhone(String phone);

        Optional<SysUser> findByNickname(String nickname);

        @Query("SELECT DISTINCT u FROM SysUser u " +
                        "LEFT JOIN SysUserAuth a ON a.user = u " +
                        "WHERE u.nickname LIKE %:keyword% " +
                        "OR u.realName LIKE %:keyword% " +
                        "OR a.identifier LIKE %:keyword%")
        Page<SysUser> searchUsers(@Param("keyword") String keyword, Pageable pageable);
}
