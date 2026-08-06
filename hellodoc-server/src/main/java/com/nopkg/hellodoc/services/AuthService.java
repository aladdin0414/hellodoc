package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.SysRole;
import com.nopkg.hellodoc.entities.SysRefreshToken;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserAuth;
import com.nopkg.hellodoc.entities.SysUserRole;
import com.nopkg.hellodoc.entities.SysUserRoleId;
import com.nopkg.hellodoc.security.JwtTokenProvider;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.repositories.RefreshTokenRepository;
import com.nopkg.hellodoc.repositories.RoleRepository;
import com.nopkg.hellodoc.repositories.UserAuthRepository;
import com.nopkg.hellodoc.repositories.UserRoleRepository;
import com.nopkg.hellodoc.repositories.UserRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.AuthResponse;
import com.nopkg.hellodoc.web.dto.LoginRequest;
import com.nopkg.hellodoc.web.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * 认证服务
 * 使用新的三表结构: sys_user + sys_user_auth
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final LoginLogService loginLogService;

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // 从 sys_user_auth 表查找用户
        SysUserAuth auth = userAuthRepository.findByIdentifierAndIdentityType(request.getUsername(), "PASSWORD")
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.USERNAME_OR_PASSWORD_ERROR));

        SysUser user = auth.getUser();

        // 检查账户是否被禁用 (0 为正常，非 0 为禁用)
        if (user.getStatus() != null && user.getStatus() != 0) {
            throw new BusinessException(ApiResponse.Code.ACCOUNT_DISABLED);
        }

        String accessToken = jwtTokenProvider.generateToken(userDetails);
        String refreshToken = issueRefreshToken(userDetails, user, UUID.randomUUID().toString(), null);

        // 记录登录日志
        loginLogService.recordLogin(user.getId(), auth.getId(), null, null, null, true);

        return new AuthResponse(accessToken, refreshToken, user.getNickname(), user.getAvatar(), false);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        // 1. 验证 Refresh Token（包括过期检查）
        if (!jwtTokenProvider.validateTokenStrict(refreshToken)) {
            throw new BusinessException(ApiResponse.Code.TOKEN_INVALID);
        }

        // 2. 仅允许 refresh token 用于刷新
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(ApiResponse.Code.TOKEN_TYPE_ERROR, "Invalid token type: refresh token required");
        }

        // 3. 从 Token 中解析用户名
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        if (username == null) {
            throw new BusinessException(ApiResponse.Code.TOKEN_TYPE_ERROR, "Invalid refresh token: username missing");
        }

        String jti = jwtTokenProvider.getJtiFromToken(refreshToken);
        String familyId = jwtTokenProvider.getFamilyIdFromToken(refreshToken);
        if (!StringUtils.hasText(jti) || !StringUtils.hasText(familyId)) {
            throw new BusinessException(ApiResponse.Code.TOKEN_INVALID, "Invalid refresh token: missing jti/family");
        }

        Instant now = Instant.now();
        SysRefreshToken tokenRecord = refreshTokenRepository.findByJtiForUpdate(jti)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.TOKEN_INVALID, "Refresh token not found"));

        // 复用检测：已轮转或已吊销的 token 再次使用，吊销整个 family
        if (tokenRecord.getUsedAt() != null || tokenRecord.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(tokenRecord.getFamilyId(), now, "REUSE_DETECTED");
            throw new BusinessException(ApiResponse.Code.TOKEN_INVALID, "Refresh token reuse detected");
        }

        if (!familyId.equals(tokenRecord.getFamilyId())) {
            throw new BusinessException(ApiResponse.Code.TOKEN_INVALID, "Refresh token family mismatch");
        }

        if (tokenRecord.getExpiresAt() != null && tokenRecord.getExpiresAt().isBefore(now)) {
            throw new BusinessException(ApiResponse.Code.TOKEN_INVALID, "Refresh token expired");
        }

        // 4. 获取用户并校验 token 归属
        SysUserAuth auth = userAuthRepository.findByIdentifierAndIdentityType(username, "PASSWORD")
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.TOKEN_INVALID, "User not found"));
        SysUser user = auth.getUser();
        if (!user.getId().equals(tokenRecord.getUser().getId())) {
            throw new BusinessException(ApiResponse.Code.TOKEN_INVALID, "Refresh token user mismatch");
        }

        // 检查账户是否被禁用
        if (user.getStatus() != null && user.getStatus() != 0) {
            throw new BusinessException(ApiResponse.Code.ACCOUNT_DISABLED);
        }

        // 5. 加载 UserDetails 并生成新 token
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
        String newAccessToken = jwtTokenProvider.generateToken(userDetails);
        String newRefreshJti = UUID.randomUUID().toString();
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userDetails, newRefreshJti, familyId);

        SysRefreshToken newRecord = new SysRefreshToken();
        newRecord.setUser(user);
        newRecord.setJti(newRefreshJti);
        newRecord.setFamilyId(familyId);
        newRecord.setParentJti(tokenRecord.getJti());
        newRecord.setIssuedAt(now);
        newRecord.setExpiresAt(jwtTokenProvider.getExpirationDateFromToken(newRefreshToken).toInstant());
        newRecord.setCreateTime(now);
        refreshTokenRepository.save(newRecord);

        // 6. 标记旧 token 已轮转（失效）
        tokenRecord.setUsedAt(now);
        tokenRecord.setRevokedAt(now);
        tokenRecord.setRevokedReason("ROTATED");
        tokenRecord.setReplacedByJti(newRefreshJti);
        refreshTokenRepository.save(tokenRecord);

        return new AuthResponse(newAccessToken, newRefreshToken, user.getNickname(), user.getAvatar());
    }

    private String issueRefreshToken(UserDetails userDetails, SysUser user, String familyId, String parentJti) {
        String jti = UUID.randomUUID().toString();
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails, jti, familyId);
        Instant now = Instant.now();

        SysRefreshToken record = new SysRefreshToken();
        record.setUser(user);
        record.setJti(jti);
        record.setFamilyId(familyId);
        record.setParentJti(parentJti);
        record.setIssuedAt(now);
        record.setExpiresAt(jwtTokenProvider.getExpirationDateFromToken(refreshToken).toInstant());
        record.setCreateTime(now);
        refreshTokenRepository.save(record);
        return refreshToken;
    }

    @Transactional
    public SysUser register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userAuthRepository.existsByIdentifierAndIdentityType(request.getUsername(), "PASSWORD")) {
            throw new BusinessException(ApiResponse.Code.USERNAME_CONFLICT);
        }

        // 1. 创建 SysUser 实体
        SysUser user = new SysUser();
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setRealName(StringUtils.hasText(request.getRealName()) ? request.getRealName() : null);
        user.setEmail(StringUtils.hasText(request.getEmail()) ? request.getEmail() : null);
        user.setPhone(StringUtils.hasText(request.getPhone()) ? request.getPhone() : null);
        user.setStatus((short) 0); // 正常状态
        user.setCreateTime(Instant.now());
        user.setUpdateTime(Instant.now());

        SysUser savedUser = userRepository.save(user);

        // 2. 创建 SysUserAuth 实体 (PASSWORD 类型)
        SysUserAuth auth = new SysUserAuth();
        auth.setUser(savedUser);
        auth.setIdentityType("PASSWORD");
        auth.setIdentifier(request.getUsername());
        auth.setCredential(passwordEncoder.encode(request.getPassword()));
        auth.setStatus((short) 0); // 正常状态
        auth.setVerified(true);
        auth.setCreateTime(Instant.now());

        userAuthRepository.save(auth);

        // 3. 分配默认角色 (user 或 common)
        SysRole defaultRole = roleRepository.findByRoleCode("user")
                .or(() -> roleRepository.findByRoleCode("common"))
                .orElse(null);

        if (defaultRole != null) {
            SysUserRole userRole = new SysUserRole();
            SysUserRoleId userRoleId = new SysUserRoleId();
            userRoleId.setUserId(savedUser.getId());
            userRoleId.setRoleId(defaultRole.getId());
            userRole.setId(userRoleId);
            userRole.setUser(savedUser);
            userRole.setRole(defaultRole);
            userRoleRepository.save(userRole);
        }

        return savedUser;
    }

    @Transactional
    public void logout(String refreshToken, String username) {
        Instant now = Instant.now();
        if (StringUtils.hasText(refreshToken)) {
            try {
                String familyId = jwtTokenProvider.getFamilyIdFromToken(refreshToken);
                if (StringUtils.hasText(familyId)) {
                    refreshTokenRepository.revokeFamily(familyId, now, "LOGOUT");
                }
            } catch (Exception ignored) {
            }
        }
        if (StringUtils.hasText(username) && !"anonymousUser".equals(username)) {
            userAuthRepository.findByIdentifierAndIdentityType(username, "PASSWORD")
                    .ifPresent(auth -> refreshTokenRepository.revokeByUserId(auth.getUser().getId(), now, "LOGOUT"));
        }
    }
}
