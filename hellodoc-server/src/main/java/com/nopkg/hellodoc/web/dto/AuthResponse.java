package com.nopkg.hellodoc.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private String nickname;
    private String avatar;
    private Boolean isInitialPwd;

    public AuthResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
    }

    public AuthResponse(String accessToken, String refreshToken, String nickname, String avatar) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.nickname = nickname;
        this.avatar = avatar;
        this.isInitialPwd = false;
    }

    public AuthResponse(String accessToken, String refreshToken, String nickname, String avatar, Boolean isInitialPwd) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.nickname = nickname;
        this.avatar = avatar;
        this.isInitialPwd = isInitialPwd;
    }
}
