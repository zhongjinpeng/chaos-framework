package com.chaos.tenant.access;

/**
 * Decides whether the current tenant satisfies an application-defined endpoint access policy.
 */
@FunctionalInterface
public interface TenantAccessAuthorizer {

    /**
     * Determines whether the tenant may invoke an endpoint guarded by the supplied policy.
     *
     * @param policy policy declared on the endpoint
     * @param tenantId authenticated tenant ID; may be {@code null} for policies that allow
     *        anonymous access
     * @return {@code true} when invocation is allowed
     */
    boolean isAllowed(String policy, String tenantId);
}
