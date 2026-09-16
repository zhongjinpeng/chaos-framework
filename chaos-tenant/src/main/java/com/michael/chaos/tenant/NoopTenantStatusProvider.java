package com.michael.chaos.tenant;

/**
 * 默认租户状态提供者。
 *
 * <p>该实现不访问任何外部系统。存在租户 ID 时视为正常租户，缺失租户 ID 时返回未知状态。</p>
 */
public class NoopTenantStatusProvider implements TenantStatusProvider {

    /**
     * 返回默认租户状态。
     */
    @Override
    public TenantDescriptor get(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return TenantDescriptor.unknown("");
        }
        return TenantDescriptor.active(tenantId);
    }
}
