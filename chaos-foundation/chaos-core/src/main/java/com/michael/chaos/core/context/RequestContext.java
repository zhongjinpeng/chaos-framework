package com.michael.chaos.core.context;

import java.util.Optional;

/**
 * 跨 Web、RPC 与 MQ 边界传播的请求级业务上下文。
 */
public final class RequestContext {

    private static final ThreadLocal<RequestContextSnapshot> CONTEXT = new ThreadLocal<>();

    private RequestContext() {
    }

    /**
     * 设置当前线程请求上下文快照。
     *
     * @param snapshot 请求上下文快照
     */
    public static void set(RequestContextSnapshot snapshot) {
        CONTEXT.set(snapshot);
    }

    /**
     * 更新当前线程用户 ID，并保留已有 trace、span、租户和应用信息。
     *
     * @param userId 用户 ID
     */
    public static void setUserId(String userId) {
        RequestContextSnapshot snapshot = current().orElse(new RequestContextSnapshot("", "", "", "", ""));
        CONTEXT.set(snapshot.withUserId(userId));
    }

    /**
     * 更新当前线程租户 ID，并保留已有 trace、span、用户和应用信息。
     *
     * @param tenantId 租户 ID
     */
    public static void setTenantId(String tenantId) {
        RequestContextSnapshot snapshot = current().orElse(new RequestContextSnapshot("", "", "", "", ""));
        CONTEXT.set(snapshot.withTenantId(tenantId));
    }

    /**
     * 获取当前线程请求上下文。
     */
    public static Optional<RequestContextSnapshot> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    /**
     * 获取当前租户 ID；不存在时返回空字符串。
     */
    public static String tenantId() {
        return current().map(RequestContextSnapshot::tenantId).orElse("");
    }

    /**
     * 获取当前用户 ID；不存在时返回空字符串。
     */
    public static String userId() {
        return current().map(RequestContextSnapshot::userId).orElse("");
    }

    /**
     * 清理当前线程请求上下文，避免线程复用导致上下文串扰。
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
