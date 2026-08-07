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
@Tag(name = "User Management", description = "User CRUD and password management APIs")
public class UserController {

    private final UserService userService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Get user list", description = "Get list of all registered users")
    public ApiResponse<Page<SysUserDetailVO>> listUsers(
            @ParameterObject @ModelAttribute SysUser user,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        return ApiResponse.success(userService.selectUserList(user, pageable).map(SysUserDetailVO::from));
    }

    @GetMapping("/search")
    @Operation(summary = "Search users", description = "Search users by keyword (nickname, real name, username)")
    public ApiResponse<Page<SysUserDetailVO>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        return ApiResponse.success(userService.searchUsers(keyword, pageable).map(SysUserDetailVO::from));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get currently authenticated user info")
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
    @Operation(summary = "Get user details", description = "Get user details by username")
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
    @Operation(summary = "Create user", description = "Create new user with role (admin/user)")
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
    @Operation(summary = "Update user", description = "Update user profile and status")
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
    @Operation(summary = "Delete user", description = "Delete specified user")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/reset-pwd")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Reset password", description = "Reset user password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody String newPassword) {
        userService.resetPassword(id, newPassword);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/init-pwd")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Initialize password", description = "Initialize user password to default 11111")
    public ApiResponse<Void> initPassword(@PathVariable Long id) {
        userService.initPassword(id);
        return ApiResponse.success(null);
    }

    record ChangePasswordRequest(String oldPassword, String newPassword) {
    }

    @PutMapping("/change-pwd")
    @Operation(summary = "Change password", description = "Change current user password")
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
    @Operation(summary = "Update profile", description = "Update current user profile info")
    public ApiResponse<Void> updateProfile(@RequestBody UpdateProfileRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.updateProfile(username, request.nickname(), request.realName(), request.email(), request.phone());
        return ApiResponse.success(null);
    }

    record UpdateLanguageRequest(String languageMode) {
    }

    @PutMapping("/language")
    @Operation(summary = "Update language preference", description = "Update current user language preference: AUTO/zh-CN/en-US")
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
    @Operation(summary = "Update avatar URL", description = "Update current user avatar URL")
    public ApiResponse<Void> updateAvatarUrl(@RequestBody UpdateAvatarRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.updateAvatar(username, request.avatar());
        return ApiResponse.success(null);
    }

    @PostMapping("/avatar")
    @Operation(summary = "Upload and update avatar", description = "Upload avatar image file and update profile")
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
