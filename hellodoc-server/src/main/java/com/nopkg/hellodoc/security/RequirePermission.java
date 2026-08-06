package com.nopkg.hellodoc.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 * 用于标注需要特定权限的方法或类
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 权限编码数组
     */
    String[] value();

    /**
     * 多权限的逻辑关系
     */
    Logical logical() default Logical.AND;

    /**
     * 逻辑枚举
     */
    enum Logical {
        /**
         * 需要满足所有权限
         */
        AND,
        /**
         * 满足任一权限即可
         */
        OR
    }
}
