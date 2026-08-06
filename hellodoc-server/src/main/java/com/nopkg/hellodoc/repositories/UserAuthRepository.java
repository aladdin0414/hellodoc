package com.nopkg.hellodoc.repositories;

import com.nopkg.hellodoc.entities.SysUserAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 用户认证身份仓库
 */
public interface UserAuthRepository extends JpaRepository<SysUserAuth, Long> {

    /**
     * 根据登录标识和身份类型查找认证记录
     * 
     * @param identifier   登录标识（手机号/邮箱/用户名/openid等）
     * @param identityType 身份类型（PASSWORD/SMS/WECHAT/ALIPAY/DOUYIN/APPLE/SSO）
     */
    @Query("select a from SysUserAuth a join fetch a.user where a.identifier = :identifier and a.identityType = :identityType")
    Optional<SysUserAuth> findByIdentifierAndIdentityType(@Param("identifier") String identifier,
            @Param("identityType") String identityType);

    @Query("select u.languageMode from SysUserAuth a join a.user u where a.identifier = :identifier and a.identityType = :identityType")
    Optional<String> findLanguageModeByIdentifierAndIdentityType(@Param("identifier") String identifier,
            @Param("identityType") String identityType);

    /**
     * 根据用户ID查找所有认证记录
     */
    List<SysUserAuth> findByUserId(Long userId);

    List<SysUserAuth> findByUserIdInAndIdentityType(List<Long> userIds, String identityType);

    /**
     * 检查指定标识和类型的认证是否已存在
     */
    boolean existsByIdentifierAndIdentityType(String identifier, String identityType);

    /**
     * 根据登录标识查找（可能有多种认证方式）
     */
    List<SysUserAuth> findByIdentifier(String identifier);
}
