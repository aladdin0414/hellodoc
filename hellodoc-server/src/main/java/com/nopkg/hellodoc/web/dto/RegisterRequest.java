package com.nopkg.hellodoc.web.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username; // 用于 sys_user_auth.identifier
    private String password; // 用于 sys_user_auth.credential
    private String nickname; // 用于 sys_user.nickname
    private String email; // 用于 sys_user.email
    private String phone; // 用于 sys_user.phone (原 phonenumber)
    private String realName; // 用于 sys_user.real_name (新增)
}
