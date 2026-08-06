package com.nopkg.hellodoc.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import com.nopkg.hellodoc.common.util.DigestUtil;

public class Sha256PasswordEncoder implements PasswordEncoder {
    private final String pepper;
    private final boolean base64;

    public Sha256PasswordEncoder(String pepper, String output) {
        this.pepper = pepper == null ? "" : pepper;
        this.base64 = output != null && output.equalsIgnoreCase("base64");
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword 不能为空");
        }
        String s = pepper + rawPassword.toString();
        try {
            return DigestUtil.sha256(s, base64);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 加密失败", e);
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return encode(rawPassword).equals(encodedPassword);
    }
}