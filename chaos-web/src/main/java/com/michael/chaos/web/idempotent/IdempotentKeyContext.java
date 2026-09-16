package com.michael.chaos.web.idempotent;

import java.util.Objects;

/**
 * 幂等 key 生成上下文。
 *
 * @param fixedKey 注解或调用方显式指定的固定 key
 * @param requestKey 调用方通过请求头等边界传入的 key
 * @param method 请求方法或业务动作
 * @param path 请求路径或业务资源标识
 * @param traceId 当前 traceId；每个请求都不同，不能作为幂等 key 的组成部分，仅保留用于诊断
 * @param tenantId 当前租户 ID，用于隔离不同租户的幂等 key
 * @param userId 当前用户 ID，用于隔离不同用户的幂等 key
 */
public record IdempotentKeyContext(
        String fixedKey,
        String requestKey,
        String method,
        String path,
        String traceId,
        String tenantId,
        String userId
) {

    /**
     * 规范化字段，避免 key 生成器处理空指针。
     */
    public IdempotentKeyContext {
        fixedKey = Objects.requireNonNullElse(fixedKey, "");
        requestKey = Objects.requireNonNullElse(requestKey, "");
        method = Objects.requireNonNullElse(method, "");
        path = Objects.requireNonNullElse(path, "");
        traceId = Objects.requireNonNullElse(traceId, "");
        tenantId = Objects.requireNonNullElse(tenantId, "");
        userId = Objects.requireNonNullElse(userId, "");
    }

}
