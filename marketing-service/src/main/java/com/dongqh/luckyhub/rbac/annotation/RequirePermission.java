package com.dongqh.luckyhub.rbac.annotation;


import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Documented
@com.dongqh.hmsecurity.permission.RequirePermission("")
@Target({
        ElementType.TYPE,
        ElementType.METHOD
})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    @AliasFor(annotation = com.dongqh.hmsecurity.permission.RequirePermission.class, attribute = "value")
    String value();
}
