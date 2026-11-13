package com.chaos.tenant.access;

import java.util.Map;
import java.util.function.Predicate;

/**
 * 按平台租户与普通租户模型解释接口租户访问策略。
 *
 * <p>策略名与语义由调用方通过 {@code policies} 提供，框架不绑定具体产品的策略命名。</p>
 */
public final class ConfigurableTenantAccessAuthorizer implements TenantAccessAuthorizer {

    /**
     * 策略语义。
     */
    public enum PolicyKind {
        /** 仅平台租户可调用。 */
        PLATFORM,
        /** 仅普通租户可调用。 */
        ORGANIZATION,
        /** 任意已认证租户可调用。 */
        SHARED,
        /** 免登录接口。 */
        PUBLIC
    }

    private final Predicate<String> platformTenant;

    private final Map<String, PolicyKind> policies;

    /**
     * 创建策略解释器。
     *
     * @param platformTenant 判断租户是否为平台租户
     * @param policies 策略名到语义的映射
     */
    public ConfigurableTenantAccessAuthorizer(
            Predicate<String> platformTenant, Map<String, PolicyKind> policies) {
        this.platformTenant = platformTenant;
        this.policies = Map.copyOf(policies);
    }

    @Override
    public boolean isAllowed(String policy, String tenantId) {
        PolicyKind kind = policies.get(policy);
        if (kind == null) {
            throw new IllegalStateException("未知租户访问策略: " + policy);
        }
        boolean hasTenant = tenantId != null && !tenantId.isBlank();
        return switch (kind) {
            case PLATFORM -> platformTenant.test(tenantId);
            case ORGANIZATION -> hasTenant && !platformTenant.test(tenantId);
            case SHARED -> hasTenant;
            case PUBLIC -> true;
        };
    }
}
