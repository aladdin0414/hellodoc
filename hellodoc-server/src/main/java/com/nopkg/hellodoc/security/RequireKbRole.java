package com.nopkg.hellodoc.security;

import com.nopkg.hellodoc.enums.KbRole;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireKbRole {
    KbRole value() default KbRole.VIEWER;

    String idParam() default "kbId";
}
