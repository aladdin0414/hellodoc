package com.nopkg.hellodoc.enums;

/**
 * 认证身份类型枚举
 */
public enum IdentityType {
    PASSWORD("password"), // 账号密码
    PHONE("phone"), // 手机验证码
    EMAIL("email"), // 邮箱验证码
    WECHAT("wechat"), // 微信
    ALIPAY("alipay"), // 支付宝
    GITHUB("github"), // GitHub
    GOOGLE("google"); // Google

    private final String code;

    IdentityType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 根据 code 获取枚举值
     */
    public static IdentityType fromCode(String code) {
        for (IdentityType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown IdentityType code: " + code);
    }
}
