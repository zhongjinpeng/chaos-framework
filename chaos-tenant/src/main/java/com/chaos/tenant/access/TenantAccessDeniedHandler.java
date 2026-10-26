package com.chaos.tenant.access;

/**
 * Creates the application-specific exception raised for a rejected tenant access policy.
 */
@FunctionalInterface
public interface TenantAccessDeniedHandler {

    /**
     * Creates the exception for a rejected endpoint invocation.
     *
     * @param policy rejected endpoint policy
     * @param tenantId authenticated tenant ID; may be {@code null}
     * @return exception to throw; never {@code null}
     */
    RuntimeException accessDenied(String policy, String tenantId);
}
