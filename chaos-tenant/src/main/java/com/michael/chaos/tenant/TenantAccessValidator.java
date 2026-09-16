package com.michael.chaos.tenant;

/**
 * 租户访问校验器。
 */
public class TenantAccessValidator {

    private final TenantStatusProvider tenantStatusProvider;

    private final boolean defaultFailClosed;

    /**
     * 创建租户访问校验器。
     */
    public TenantAccessValidator(TenantStatusProvider tenantStatusProvider, boolean failClosed) {
        this.tenantStatusProvider = tenantStatusProvider;
        this.defaultFailClosed = failClosed;
    }

    /**
     * 校验租户是否允许访问。
     */
    public TenantAccessDecision validate(String tenantId) {
        return validate(tenantId, defaultFailClosed);
    }

    /**
     * 使用调用方指定的 fail-closed 策略校验租户是否允许访问。
     */
    public TenantAccessDecision validate(String tenantId, boolean failClosed) {
        if (tenantId == null || tenantId.isBlank()) {
            TenantDescriptor unknown = TenantDescriptor.unknown("");
            return failClosed ? TenantAccessDecision.deny(unknown, "tenant id is missing")
                    : TenantAccessDecision.allow(unknown);
        }
        TenantDescriptor descriptor = tenantStatusProvider.get(tenantId);
        if (descriptor == null) {
            descriptor = TenantDescriptor.unknown(tenantId);
        }
        if (descriptor.accessible()) {
            return TenantAccessDecision.allow(descriptor);
        }
        return failClosed ? TenantAccessDecision.deny(descriptor, "tenant status is " + descriptor.status().name())
                : TenantAccessDecision.allow(descriptor);
    }
}
