package com.nopkg.hellodoc.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.nopkg.hellodoc.common.util.DigestUtil;

public class Sha256PasswordEncoder implements PasswordEncoder {
    private final String pepper;
    private final boolean base64;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public Sha256PasswordEncoder(String pepper, String output) {
        this.pepper = pepper == null ? "" : pepper;
        this.base64 = output != null && output.equalsIgnoreCase("base64");
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword cannot be null");
        }
        String s = pepper + rawPassword.toString();
        try {
            return DigestUtil.sha256(s, base64);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 encryption failed", e);
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        // 向后兼容支持 BCrypt 哈希密文 ($2a$, $2b$, $2y$)
        if (encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$") || encodedPassword.startsWith("$2y$")) {
            return bcrypt.matches(rawPassword, encodedPassword);
        }
        return encode(rawPassword).equals(encodedPassword);
    }
}