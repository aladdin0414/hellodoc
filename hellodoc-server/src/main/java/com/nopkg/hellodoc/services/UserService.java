package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.exceptions.ResourceNotFoundException;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.repositories.RoleRepository;
import com.nopkg.hellodoc.repositories.UserRoleRepository;
import com.nopkg.hellodoc.entities.SysRole;
import com.nopkg.hellodoc.entities.SysUserRole;
import com.nopkg.hellodoc.entities.SysUserRoleId;
import com.nopkg.hellodoc.repositories.RefreshTokenRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Optional;

/**
 * 用户服务
 * 使用新的三表结构: sys_user + sys_user_auth
 */
@Service
@RequiredArgsConstructor
public class UserService {
    private static final Set<String> ALLOWED_LANGUAGE_MODES = Set.of("AUTO", "zh-CN", "en-US");

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<SysUser> selectUserList(SysUser user, Pageable pageable) {
        Page<SysUser> page = userRepository.findAll(buildUserSpecification(user), pageable);
        populateUserNamesAndRoles(page.getContent());
        return page;
    }

    private Specification<SysUser> buildUserSpecification(SysUser user) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (user.getNickname() != null && StringUtils.hasText(user.getNickname())) {
                predicates.add(cb.like(root.get("nickname"), "%" + user.getNickname() + "%"));
            }
            if (user.getPhone() != null && StringUtils.hasText(user.getPhone())) {
                predicates.add(cb.like(root.get("phone"), "%" + user.getPhone() + "%"));
            }
            if (user.getEmail() != null && StringUtils.hasText(user.getEmail())) {
                predicates.add(cb.like(root.get("email"), "%" + user.getEmail() + "%"));
            }
            if (user.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), user.getStatus()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public List<SysUser> listUsers() {
        return userRepository.findAll();
    }

    public Optional<SysUser> getUser(Long id) {
        return userRepository.findById(id).map(user -> {
            populateUserNamesAndRoles(List.of(user));
            return user;
        });
    }

    /**
     * 根据用户名（identifier）获取用户
     */
    @Transactional(readOnly = true)
    public Optional<SysUser> getUserByUsername(String username) {
        return userAuthRepository.findByIdentifierAndIdentityType(username, "PASSWORD")
                .map(auth -> {
                    SysUser user = auth.getUser();
                    user.setUsername(auth.getIdentifier());
                    populateUserNamesAndRoles(List.of(user));
                    return user;
                });
    }

    @Transactional
    public SysUser createUser(SysUser user, String username, String password, String roleCode) {
        // 检查用户名是否已存在
        if (userAuthRepository.existsByIdentifierAndIdentityType(username, "PASSWORD")) {
            throw new BusinessException(ApiResponse.Code.USERNAME_CONFLICT);
        }

        user.setStatus((short) 0);
        user.setLanguageMode("AUTO");
        user.setCreateTime(Instant.now());
        user.setUpdateTime(Instant.now());

        // 处理空值，防止唯一索引冲突 (空字符串转 null)
        if (!StringUtils.hasText(user.getPhone())) {
            user.setPhone(null);
        }
        if (!StringUtils.hasText(user.getEmail())) {
            user.setEmail(null);
        }

        SysUser savedUser = userRepository.save(user);
        savedUser.setUsername(username);

        // 创建认证记录
        SysUserAuth auth = new SysUserAuth();
        auth.setUser(savedUser);
        auth.setIdentityType("PASSWORD");
        auth.setIdentifier(username);
        // 使用默认密码 11111 如果未提供
        String finalPassword = (password != null && !password.isEmpty()) ? password : "11111";
        auth.setCredential(passwordEncoder.encode(finalPassword));
        auth.setStatus((short) 0);
        auth.setVerified(true);
        auth.setCreateTime(Instant.now());
        userAuthRepository.save(auth);

        // 3. 分配角色 (指定角色 或 默认 user/common)
        String targetRoleCode = (roleCode != null && !roleCode.isEmpty()) ? roleCode : "user";
        SysRole role = roleRepository.findByRoleCode(targetRoleCode)
                .or(() -> roleRepository.findByRoleCode("common"))
                .orElse(null);

        if (role != null) {
            SysUserRole userRole = new SysUserRole();
            SysUserRoleId userRoleId = new SysUserRoleId();
            userRoleId.setUserId(savedUser.getId());
            userRoleId.setRoleId(role.getId());
            userRole.setId(userRoleId);
            userRole.setUser(savedUser);
            userRole.setRole(role);
            userRoleRepository.save(userRole);
        }

        return savedUser;
    }

    @Transactional
    public SysUser updateUser(Long id, SysUser userDetails, String roleCode) {
        return userRepository.findById(id).map(user -> {
            if (StringUtils.hasText(userDetails.getNickname())) {
                user.setNickname(userDetails.getNickname());
            }
            if (StringUtils.hasText(userDetails.getRealName())) {
                user.setRealName(userDetails.getRealName());
            }
            if (StringUtils.hasText(userDetails.getEmail())) {
                user.setEmail(userDetails.getEmail());
            } else {
                user.setEmail(null);
            }
            if (StringUtils.hasText(userDetails.getPhone())) {
                user.setPhone(userDetails.getPhone());
            } else {
                user.setPhone(null);
            }
            if (StringUtils.hasText(userDetails.getAvatar())) {
                user.setAvatar(userDetails.getAvatar());
            }
            if (userDetails.getStatus() != null) {
                user.setStatus(userDetails.getStatus());
            }
            user.setUpdateTime(Instant.now());
            SysUser savedUser = userRepository.save(user);

            if (userDetails.getStatus() != null && userDetails.getStatus() != 0) {
                refreshTokenRepository.revokeByUserId(savedUser.getId(), Instant.now(), "USER_DISABLED");
            }

            // 更新角色关联
            if (StringUtils.hasText(roleCode)) {
                // 删除原有关联
                userRoleRepository.deleteByIdUserId(id);
                
                // 查找新角色
                SysRole role = roleRepository.findByRoleCode(roleCode)
                        .or(() -> roleRepository.findByRoleCode("common"))
                        .orElse(null);

                if (role != null) {
                    SysUserRole userRole = new SysUserRole();
                    SysUserRoleId userRoleId = new SysUserRoleId();
                    userRoleId.setUserId(id);
                    userRoleId.setRoleId(role.getId());
                    userRole.setId(userRoleId);
                    userRole.setUser(savedUser);
                    userRole.setRole(role);
                    userRoleRepository.save(userRole);
                }
            }
            
            return savedUser;
        }).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * 重置密码
     */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        List<SysUserAuth> auths = userAuthRepository.findByUserId(id);
        auths.stream()
                .filter(a -> "PASSWORD".equals(a.getIdentityType()))
                .findFirst()
                .ifPresent(auth -> {
                    auth.setCredential(passwordEncoder.encode(newPassword));
                    userAuthRepository.save(auth);
                });
    }

    /**
     * 初始化密码为默认密码 11111
     */
    @Transactional
    public void initPassword(Long id) {
        resetPassword(id, "11111");
    }

    /**
     * 修改密码（用户自己修改）
     */
    @Transactional
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        return userAuthRepository.findByIdentifierAndIdentityType(username, "PASSWORD")
                .map(auth -> {
                    // 验证旧密码
                    if (!passwordEncoder.matches(oldPassword, auth.getCredential())) {
                        return false;
                    }
                    auth.setCredential(passwordEncoder.encode(newPassword));
                    userAuthRepository.save(auth);
                    return true;
                }).orElse(false);
    }

    /**
     * 用户更新自己的资料
     */
    @Transactional
    public void updateProfile(String username, String nickname, String realName, String email, String phone) {
        getUserByUsername(username).ifPresent(user -> {
            if (nickname != null) {
                user.setNickname(nickname);
            }
            if (realName != null) {
                user.setRealName(realName);
            }
            if (email != null) {
                user.setEmail(StringUtils.hasText(email) ? email : null);
            }
            if (phone != null) {
                user.setPhone(StringUtils.hasText(phone) ? phone : null);
            }
            user.setUpdateTime(Instant.now());
            userRepository.save(user);
        });
    }

    /**
     * 用户更新头像
     */
    @Transactional
    public void updateAvatar(String username, String avatarUrl) {
        getUserByUsername(username).ifPresent(user -> {
            user.setAvatar(avatarUrl);
            user.setUpdateTime(Instant.now());
            userRepository.save(user);
        });
    }

    @Transactional
    public void updateLanguageMode(String username, String languageMode) {
        if (!ALLOWED_LANGUAGE_MODES.contains(languageMode)) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR);
        }
        getUserByUsername(username).ifPresent(user -> {
            user.setLanguageMode(languageMode);
            user.setUpdateTime(Instant.now());
            userRepository.save(user);
        });
    }

    public String getLanguageModeOrDefault(SysUser user) {
        if (user == null || user.getLanguageMode() == null || user.getLanguageMode().isBlank()) {
            return "AUTO";
        }
        return ALLOWED_LANGUAGE_MODES.contains(user.getLanguageMode()) ? user.getLanguageMode() : "AUTO";
    }

    public Page<SysUser> searchUsers(String keyword, Pageable pageable) {
        Page<SysUser> page = userRepository.searchUsers(keyword, pageable);
        populateUserNamesAndRoles(page.getContent());
        return page;
    }

    private void populateUserNamesAndRoles(List<SysUser> users) {
        if (users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        
        // 1. 填充用户名
        List<SysUserAuth> auths = userAuthRepository.findByUserIdInAndIdentityType(userIds, "PASSWORD");
        java.util.Map<Long, String> usernameMap = auths.stream()
                .collect(java.util.stream.Collectors.toMap(
                        auth -> auth.getUser().getId(),
                        SysUserAuth::getIdentifier,
                        (v1, v2) -> v1
                ));
        
        // 2. 填充角色信息
        List<SysUserRole> userRoles = userRoleRepository.findByIdUserIdIn(userIds);
        java.util.Map<Long, List<String>> rolesMap = new java.util.HashMap<>();
        for (SysUserRole ur : userRoles) {
            rolesMap.computeIfAbsent(ur.getUser().getId(), k -> new ArrayList<>())
                    .add(ur.getRole().getRoleCode());
        }

        users.forEach(user -> {
            user.setUsername(usernameMap.get(user.getId()));
            user.setRoles(rolesMap.getOrDefault(user.getId(), new ArrayList<>()));
        });
    }
}
