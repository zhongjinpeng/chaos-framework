package com.michael.chaos.core.idempotent;

import java.time.Duration;

/**
 * 幂等 key 存储契约。
 */
public interface IdempotentRepository {

    /**
     * 仅当 key 不存在时保存。
     *
     * @param key 幂等 key
     * @param ttl key 有效期
     * @return 保存成功返回 {@code true}，已存在返回 {@code false}
     */
    boolean saveIfAbsent(String key, Duration ttl);

    /**
     * 删除幂等 key，用于业务失败后释放占位，允许调用方重试。
     *
     * <p>默认实现为空操作只是为了兼容旧实现；任何支持失败重试的仓储（例如 Redis 实现）
     * 都必须覆盖该方法，否则 HTTP 请求失败或 MQ 消费失败后，在 TTL 内重试会被误判为重复请求。</p>
     *
     * @param key 幂等 key
     */
    default void remove(String key) {
    }
}
