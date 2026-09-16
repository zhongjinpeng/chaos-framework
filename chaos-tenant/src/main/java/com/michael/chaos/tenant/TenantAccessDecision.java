package com.michael.chaos.tenant;

import java.util.Objects;

/**
 * 租户访问决策结果。
 *
 * @param allowed 是否允许访问
 * @param tenant 租户运行态描述
 * @param reason 拒绝原因
 */
public record TenantAccessDecision(
        boolean allowed,
        TenantDescriptor tenant,
        String reason
) {

    /**
     * 规范化决策结果。
     */
    public TenantAccessDecision {
        tenant = tenant == null ? TenantDescriptor.unknown("") : tenant;
        reason = Objects.requireNonNullElse(reason, "");
    }

    /**
     * 创建允许访问的决策。
     */
    public static TenantAccessDecision allow(TenantDescriptor tenant) {
        return new TenantAccessDecision(true, tenant, "");
    }

    /**
     * 创建拒绝访问的决策。
     */
    public static TenantAccessDecision deny(TenantDescriptor tenant, String reason) {
        return new TenantAccessDecision(false, tenant, reason);
    }
}
