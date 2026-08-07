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
@Tag(name = "Authentication Management", description = "User login, registration and session APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Login with username and password to get AccessToken and RefreshToken")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh Token", description = "Get new Access Token and Refresh Token via Refresh Token")
    public ApiResponse<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refreshToken(request.getRefreshToken()));
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register new user account")
    public ApiResponse<SysUser> register(@RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Logout current user and invalidate active tokens")
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
