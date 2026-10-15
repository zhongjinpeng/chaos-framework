package com.chaos.core.context;

/**
 * Servlet 请求属性中保存的认证上下文键。
 *
 * <p>请求属性用于跨越安全过滤器与访问日志过滤器的生命周期边界。线程上下文在安全过滤器
 * 返回时会恢复，访问日志仍可通过这些属性读取本次请求的最终认证身份。</p>
 */
public final class RequestContextAttributes {

    /**
     * 当前请求认证用户 ID 属性键。
     */
    public static final String USER_ID = RequestContextAttributes.class.getName() + ".userId";

    /**
     * 当前请求认证租户 ID 属性键。
     */
    public static final String TENANT_ID = RequestContextAttributes.class.getName() + ".tenantId";

    private RequestContextAttributes() {
    }
}
