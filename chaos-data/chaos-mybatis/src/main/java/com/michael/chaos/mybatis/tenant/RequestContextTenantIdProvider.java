package com.michael.chaos.mybatis.tenant;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.tenant.TenantContext;

/**
 * 从 {@link RequestContext} 获取租户 ID 的默认实现。
 */
public class RequestContextTenantIdProvider implements TenantIdProvider {

    /**
     * 返回当前请求上下文中的租户 ID。
     */
    @Override
    public String currentTenantId() {
        String tenantId = TenantContext.tenantId();
        return (tenantId == null || tenantId.isBlank()) ? RequestContext.tenantId() : tenantId;
    }
}
