package com.chaos.security.service;

import com.chaos.security.auth.LoginUserUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 已认证的服务委托身份。
 *
 * <p>身份必须由已经校验过服务令牌的过滤器写入 {@code Authentication#getDetails()}，
 * 不能直接读取未校验的 HTTP 头。</p>
 */
public final class ServiceCaller {

    private ServiceCaller() {
    }

    /**
     * 读取调用服务身份。
     *
     * @return 服务委托过滤器写入的调用方名称
     * @throws AccessDeniedException 缺少服务委托身份时抛出
     */
    public static String required() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof String caller)
                || caller.isBlank()) {
            throw new AccessDeniedException("缺少服务调用身份");
        }
        return caller;
    }

    /**
     * 校验请求租户与服务委托身份一致。
     *
     * @param tenantId 请求声明的资源归属租户
     * @throws AccessDeniedException 与认证上下文租户不一致时抛出
     */
    public static void requireTenant(String tenantId) {
        if (!LoginUserUtils.requiredTenantId().equals(tenantId)) {
            throw new AccessDeniedException("请求租户与服务委托身份不一致");
        }
    }
}
