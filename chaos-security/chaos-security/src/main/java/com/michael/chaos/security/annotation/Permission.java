package com.michael.chaos.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级权限标记。
 *
 * <p>业务系统或安全拦截器可以读取该注解，并结合 {@link com.michael.chaos.security.api.auth.LoginUser} 完成 RBAC 校验。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Permission {

    /**
     * 权限编码。
     */
    String value();
}
