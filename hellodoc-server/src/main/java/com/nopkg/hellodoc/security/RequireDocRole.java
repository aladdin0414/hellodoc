package com.nopkg.hellodoc.security;

import com.nopkg.hellodoc.enums.DocRole;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireDocRole {
    DocRole value() default DocRole.VIEWER;

    String idParam() default "docId";
}
