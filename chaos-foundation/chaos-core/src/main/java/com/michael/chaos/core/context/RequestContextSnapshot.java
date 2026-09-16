package com.michael.chaos.core.context;

import java.util.Objects;

/**
 * 请求上下文不可变快照。
 *
 * <p>租户 ID 和用户 ID 在构造时强制经过 {@link ContextIdentifiers} 白名单校验。
 * 这是所有上下文来源（HTTP 请求头、MQ 消息头、认证结果、业务代码）的统一入口，
 * 保证下游 SQL 改写、MDC 和出站请求头拿到的值不含引号、换行等危险字符。</p>
 *
 * @param traceId 链路追踪 ID
 * @param spanId 当前调用跨度 ID
 * @param tenantId 当前租户 ID，非法值会被规范化为空字符串
 * @param userId 当前用户 ID，非法值会被规范化为空字符串
 * @param appName 当前应用名称
 */
public record RequestContextSnapshot(
        String traceId,
        String spanId,
        String tenantId,
        String userId,
        String appName
) {

    /**
     * 规范化上下文字段，避免下游 MDC、SQL 插件和日志组件处理 {@code null} 或非法字符。
     */
    public RequestContextSnapshot {
        traceId = Objects.requireNonNullElse(traceId, "");
        spanId = Objects.requireNonNullElse(spanId, "");
        tenantId = ContextIdentifiers.sanitizeIdentifier("tenantId", tenantId);
        userId = ContextIdentifiers.sanitizeIdentifier("userId", userId);
        appName = Objects.requireNonNullElse(appName, "");
    }

    /**
     * 创建空快照。
     */
    public static RequestContextSnapshot empty() {
        return new RequestContextSnapshot("", "", "", "", "");
    }

    public RequestContextSnapshot withTenantId(String tenantId) {
        return new RequestContextSnapshot(this.traceId, this.spanId, tenantId, this.userId, this.appName);
    }

    public RequestContextSnapshot withUserId(String userId) {
        return new RequestContextSnapshot(this.traceId, this.spanId, this.tenantId, userId, this.appName);
    }
}
