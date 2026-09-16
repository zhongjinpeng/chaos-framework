package com.michael.chaos.tenant;

/**
 * 租户状态查询端口。
 *
 * <p>framework 只依赖该端口；业务系统可以用数据库、配置中心或租户中心实现它。</p>
 */
@FunctionalInterface
public interface TenantStatusProvider {

    /**
     * 查询租户运行态描述。
     *
     * @param tenantId 租户 ID
     * @return 租户运行态描述
     */
    TenantDescriptor get(String tenantId);
}
