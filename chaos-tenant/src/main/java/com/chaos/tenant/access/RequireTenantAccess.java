package com.chaos.tenant.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the application-defined tenant access policy required by an HTTP endpoint.
 *
 * <p>Method declarations override type declarations. Policy names are interpreted by the
 * application's {@link TenantAccessAuthorizer}; this annotation does not grant cross-tenant data
 * access by itself.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireTenantAccess {

    /**
     * Returns the application-defined policy name.
     *
     * @return non-blank policy name understood by the configured authorizer
     */
    String value();
}
