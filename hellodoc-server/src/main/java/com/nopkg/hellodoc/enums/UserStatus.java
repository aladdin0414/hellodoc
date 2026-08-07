package com.nopkg.hellodoc.enums;

/**
 * 用户状态枚举
 */
public enum UserStatus {
    NORMAL(0, "Normal"),
    FROZEN(1, "Frozen"),
    CANCELLED(2, "Cancelled"),
    DELETED(3, "Deleted");

    private final int code;
    private final String description;

    UserStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code 获取枚举值
     */
    public static UserStatus fromCode(int code) {
        for (UserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown UserStatus code: " + code);
    }
}
