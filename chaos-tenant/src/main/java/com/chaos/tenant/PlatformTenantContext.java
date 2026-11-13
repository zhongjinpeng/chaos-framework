package com.chaos.tenant;

import java.util.function.Supplier;

/**
 * 平台租户与当前登录租户上下文。
 *
 * <p>平台租户维护跨机构共享配置；当前租户由调用方注入读取器，框架不直接依赖认证实现。</p>
 */
public final class PlatformTenantContext {

    private final String platformTenantId;

    private final Supplier<String> currentTenant;

    /**
     * 创建平台租户上下文。
     *
     * @param platformTenantId 平台维护跨机构共享配置的租户标识
     * @param currentTenant 当前登录租户读取器，必须返回已认证租户
     */
    public PlatformTenantContext(String platformTenantId, Supplier<String> currentTenant) {
        this.platformTenantId = platformTenantId;
        this.currentTenant = currentTenant;
    }

    /**
     * 返回平台租户标识。
     *
     * @return 平台维护跨机构共享配置的租户标识
     */
    public String platformTenantId() {
        return platformTenantId;
    }

    /**
     * 返回当前登录租户标识。
     *
     * @return 从调用方注入的读取器取得的租户标识
     */
    public String currentTenantId() {
        return currentTenant.get();
    }

    /**
     * 判断当前登录租户是否为平台租户。
     *
     * @return 当前租户与平台租户相同时返回 {@code true}
     */
    public boolean isPlatformTenant() {
        return platformTenantId.equals(currentTenant.get());
    }

    /**
     * 判断指定租户是否为平台租户，不读取当前登录上下文。
     *
     * @param tenantId 待判断的租户 ID；可为空
     * @return 租户 ID 与平台租户 ID 相同时返回 {@code true}
     */
    public boolean isPlatformTenant(String tenantId) {
        return platformTenantId.equals(tenantId);
    }

    /**
     * 要求当前登录租户为平台租户，并由调用模块定义边界失败异常。
     *
     * @param exceptionSupplier 当前租户不是平台租户时创建业务异常的函数
     * @throws RuntimeException 当前登录租户不是平台租户时抛出调用模块提供的异常
     */
    public void requirePlatformTenant(Supplier<? extends RuntimeException> exceptionSupplier) {
        if (!isPlatformTenant()) {
            throw exceptionSupplier.get();
        }
    }

    /**
     * 要求当前登录租户为非平台机构租户，并由调用模块定义边界失败异常。
     *
     * @param exceptionSupplier 当前租户是平台租户时创建业务异常的函数
     * @return 当前机构租户标识
     * @throws RuntimeException 当前登录租户是平台租户时抛出调用模块提供的异常
     */
    public String requireOrganizationTenant(Supplier<? extends RuntimeException> exceptionSupplier) {
        String tenantId = currentTenant.get();
        if (platformTenantId.equals(tenantId)) {
            throw exceptionSupplier.get();
        }
        return tenantId;
    }
}
