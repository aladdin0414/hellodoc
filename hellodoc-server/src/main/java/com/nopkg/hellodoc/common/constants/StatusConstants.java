package com.nopkg.hellodoc.common.constants;

/**
 * 系统范围的状态和角色常量。
 */
public final class StatusConstants {

    private StatusConstants() {
        // 防止实例化
    }

    // 状态值
    public static final String STATUS_NORMAL = "0";
    public static final String STATUS_DISABLED = "1";

    // 删除标志
    public static final String DEL_FLAG_NORMAL = "0";
    public static final String DEL_FLAG_DELETED = "1";

    // 角色键
    public static final String ADMIN_ROLE_KEY = "admin";
    public static final String DEFAULT_ROLE_KEY = "common";

    // 管理员用户 ID（为了向后兼容）
    public static final Long ADMIN_USER_ID = 1L;

    // 管理员用户名
    public static final String ADMIN_USERNAME = "admin";
}
