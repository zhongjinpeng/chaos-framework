package com.michael.chaos.tenant;

/**
 * 租户生命周期状态。
 */
public enum TenantStatus {

    /**
     * 正常可访问。
     */
    ACTIVE(true),

    /**
     * 已冻结，不允许访问业务资源。
     */
    FROZEN(false),

    /**
     * 已禁用，不允许访问业务资源。
     */
    DISABLED(false),

    /**
     * 已过期，不允许访问业务资源。
     */
    EXPIRED(false),

    /**
     * 已删除，不允许访问业务资源。
     */
    DELETED(false),

    /**
     * 租户不存在或状态未知。
     */
    UNKNOWN(false);

    private final boolean accessible;

    TenantStatus(boolean accessible) {
        this.accessible = accessible;
    }

    /**
     * 当前状态是否允许访问业务资源。
     */
    public boolean isAccessible() {
        return accessible;
    }
}
