package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.services.UserService;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.user.SysUserDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * 用户管理控制器
 * 使用新的三表结构
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户增删改查及密码重置")
public class UserController {

    private final UserService userService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "获取用户列表", description = "获取所有用户列表")
    public ApiResponse<Page<SysUserDetailVO>> listUsers(
            @ParameterObject @ModelAttribute SysUser user,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        return ApiResponse.success(userService.selectUserList(user, pageable).map(SysUserDetailVO::from));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索用户", description = "根据关键字（昵称、姓名、用户名）搜索用户")
    public ApiResponse<Page<SysUserDetailVO>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        return ApiResponse.success(userService.searchUsers(keyword, pageable).map(SysUserDetailVO::from));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户", description = "获取当前登录用户的信息")
    public ApiResponse<UserInfo> getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.getUserByUsername(username)
                .map(user -> {
                    UserInfo userInfo = new UserInfo(
                            user.getId(),
                            user.getUsername(),
                            user.getNickname(),
                            user.getRealName(),
                            user.getEmail(),
                            user.getPhone(),
                            user.getAvatar(),
                            user.getRoles().stream().findFirst().orElse("user"),
                            userService.getLanguageModeOrDefault(user));
                    return ApiResponse.success(userInfo);
                })
                .orElse(ApiResponse.error(ApiResponse.Code.UNAUTHORIZED));
    }

    record UserInfo(Long id, String username, String nickname, String realName, String email, String phone,
            String avatar, String role, String languageMode) {
    }

    @GetMapping("/getUserInfo/{username}")
    @Operation(summary = "获取用户详情", description = "根据用户名获取用户详情")
    public ApiResponse<UserInfo> getUserInfo(@PathVariable String username) {
        return userService.getUserByUsername(username)
                .map(user -> {
                    UserInfo userInfo = new UserInfo(
                            user.getId(),
                            user.getUsername(),
                            user.getNickname(),
                            user.getRealName(),
                            user.getEmail(),
                            user.getPhone(),
                            user.getAvatar(),
                            user.getRoles().stream().findFirst().orElse("user"),
                            userService.getLanguageModeOrDefault(user));
                    return ApiResponse.success(userInfo);
                })
                .orElse(ApiResponse.error(ApiResponse.Code.RESOURCE_NOT_FOUND));
    }

    record CreateUserRequest(String username, String password, String nickname, String realName, String email,
            String phone, String role) { // Added role
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "创建用户", description = "创建新用户，支持指定角色（admin/user），密码为空默认为11111")
    public ApiResponse<SysUserDetailVO> createUser(@RequestBody CreateUserRequest request) {
        SysUser user = new SysUser();
        user.setNickname(request.nickname());
        user.setRealName(request.realName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        return ApiResponse
                .success(SysUserDetailVO
                        .from(userService.createUser(user, request.username(), request.password(), request.role())));
    }

    record UpdateUserRequest(String nickname, String realName, String email, String phone, String avatar, Short status, String role) {}

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "更新用户", description = "更新用户信息")
    public ApiResponse<SysUserDetailVO> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        SysUser user = new SysUser();
        user.setNickname(request.nickname());
        user.setRealName(request.realName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setAvatar(request.avatar());
        user.setStatus(request.status());
        return ApiResponse.success(SysUserDetailVO.from(userService.updateUser(id, user, request.role())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "删除用户", description = "删除指定用户")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/reset-pwd")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "重置密码", description = "重置用户密码")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody String newPassword) {
        userService.resetPassword(id, newPassword);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/init-pwd")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "初始化密码", description = "将用户密码初始化为默认密码 11111")
    public ApiResponse<Void> initPassword(@PathVariable Long id) {
        userService.initPassword(id);
        return ApiResponse.success(null);
    }

    record ChangePasswordRequest(String oldPassword, String newPassword) {
    }

    @PutMapping("/change-pwd")
    @Operation(summary = "修改密码", description = "用户修改自己的密码")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean success = userService.changePassword(username, request.oldPassword(), request.newPassword());
        if (success) {
            return ApiResponse.success(null);
        } else {
            return ApiResponse.error(ApiResponse.Code.OLD_PASSWORD_WRONG);
        }
    }

    record UpdateProfileRequest(String nickname, String realName, String email, String phone) {
    }

    @PutMapping("/profile")
    @Operation(summary = "更新个人资料", description = "用户更新自己的基本资料")
    public ApiResponse<Void> updateProfile(@RequestBody UpdateProfileRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.updateProfile(username, request.nickname(), request.realName(), request.email(), request.phone());
        return ApiResponse.success(null);
    }

    record UpdateLanguageRequest(String languageMode) {
    }

    @PutMapping("/language")
    @Operation(summary = "更新语言偏好", description = "更新当前用户语言偏好：AUTO/zh-CN/en-US")
    public ApiResponse<Void> updateLanguage(@RequestBody UpdateLanguageRequest request) {
        if (request == null || request.languageMode() == null) {
            return ApiResponse.error(ApiResponse.Code.INVALID_REQUEST);
        }
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.updateLanguageMode(username, request.languageMode());
        return ApiResponse.success(null);
    }

    record UpdateAvatarRequest(String avatar) {
    }

    @PutMapping("/avatar")
    @Operation(summary = "更新头像网址", description = "用户更新自己的头像网址")
    public ApiResponse<Void> updateAvatarUrl(@RequestBody UpdateAvatarRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.updateAvatar(username, request.avatar());
        return ApiResponse.success(null);
    }

    @PostMapping("/avatar")
    @Operation(summary = "上传并更新头像", description = "上传头像文件并自动更新个人资料")
    public ApiResponse<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error(ApiResponse.Code.UPLOAD_FILE_REQUIRED);
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ApiResponse.error(ApiResponse.Code.UPLOAD_IMAGE_ONLY);
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            return ApiResponse.error(ApiResponse.Code.UPLOAD_AVATAR_TOO_LARGE);
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = "avatar_" + UUID.randomUUID().toString().replace("-", "") + extension;

            Path avatarDir = Paths.get(uploadDir, "avatars");
            if (!Files.exists(avatarDir)) {
                Files.createDirectories(avatarDir);
            }

            Path targetPath = avatarDir.resolve(newFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/api/files/avatars/" + newFilename;

            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            if (username != null && !"anonymousUser".equals(username)) {
                userService.updateAvatar(username, fileUrl);
            }

            return ApiResponse.success(Map.of(
                    "url", fileUrl,
                    "filename", newFilename));
        } catch (IOException e) {
            return ApiResponse.error(ApiResponse.Code.UPLOAD_AVATAR_FAILED);
        }
    }
}
