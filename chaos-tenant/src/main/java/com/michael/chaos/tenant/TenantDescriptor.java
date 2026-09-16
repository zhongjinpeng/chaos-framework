package com.michael.chaos.tenant;

import java.util.Objects;

/**
 * 租户运行态描述。
 *
 * @param tenantId 租户 ID
 * @param status 租户状态
 * @param plan 套餐信息
 * @param isolationMode 数据隔离模式
 */
public record TenantDescriptor(
        String tenantId,
        TenantStatus status,
        TenantPlan plan,
        TenantIsolationMode isolationMode
) {

    /**
     * 规范化租户描述字段。
     */
    public TenantDescriptor {
        tenantId = Objects.requireNonNullElse(tenantId, "").trim();
        status = Objects.requireNonNullElse(status, TenantStatus.UNKNOWN);
        plan = plan == null ? TenantPlan.empty() : plan;
        isolationMode = Objects.requireNonNullElse(isolationMode, TenantIsolationMode.SHARED_SCHEMA);
    }

    /**
     * 创建未知租户描述。
     */
    public static TenantDescriptor unknown(String tenantId) {
        return new TenantDescriptor(tenantId, TenantStatus.UNKNOWN, TenantPlan.empty(), TenantIsolationMode.SHARED_SCHEMA);
    }

    /**
     * 创建正常租户描述。
     */
    public static TenantDescriptor active(String tenantId) {
        return new TenantDescriptor(tenantId, TenantStatus.ACTIVE, TenantPlan.empty(), TenantIsolationMode.SHARED_SCHEMA);
    }

    /**
     * 当前租户是否允许访问业务资源。
     */
    public boolean accessible() {
        return status.isAccessible();
    }
}
