package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.SysRole;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.repositories.RoleRepository;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义用户详情服务
 * 使用新的三表结构: sys_user + sys_user_auth
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAuthRepository userAuthRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 从 sys_user_auth 表查找 PASSWORD 类型的认证记录
        SysUserAuth auth = userAuthRepository.findByIdentifierAndIdentityType(username, "PASSWORD")
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        SysUser user = auth.getUser();
        if (user == null) {
            throw new UsernameNotFoundException("User entity not found for auth: " + username);
        }

        List<GrantedAuthority> authorities = new ArrayList<>();

        // 加载角色
        List<SysRole> roles = roleRepository.findRolesByUserId(user.getId());

        for (SysRole role : roles) {

            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()));
        }

        boolean enabled = user.getStatus() == null || user.getStatus() == 0;
        boolean accountNonExpired = true;
        boolean credentialsNonExpired = true;
        boolean accountNonLocked = true;

        return new org.springframework.security.core.userdetails.User(
                auth.getIdentifier(), // 使用 identifier 作为用户名
                auth.getCredential(), // 使用 credential 作为密码
                enabled,
                accountNonExpired,
                credentialsNonExpired,
                accountNonLocked,
                authorities);
    }
}
