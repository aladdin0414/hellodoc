package com.nopkg.hellodoc.controllers;

import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.services.AuthService;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.AuthResponse;
import com.nopkg.hellodoc.web.dto.LoginRequest;
import com.nopkg.hellodoc.web.dto.RegisterRequest;
import com.nopkg.hellodoc.web.dto.RefreshTokenRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录、注册等接口")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "通过用户名和密码登录，返回 AccessToken 和 RefreshToken")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "刷新 Token", description = "通过 Refresh Token 获取新的 Access Token 和 Refresh Token")
    public ApiResponse<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refreshToken(request.getRefreshToken()));
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户")
    public ApiResponse<SysUser> register(@RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "登出当前用户，使当前 Token 失效")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        String username = null;
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            username = auth.getName();
        }
        authService.logout(request != null ? request.getRefreshToken() : null, username);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        return ApiResponse.success(null);
    }
}
