package com.michael.chaos.core.idempotent;

import java.time.Duration;
import java.util.Optional;

/**
 * 幂等响应快照存储契约。
 *
 * <p>与 {@link IdempotentRepository} 分工：仓储负责"这个 key 有没有被占用"（执行前占位、失败后释放），
 * 本接口负责"这个 key 第一次执行的结果是什么"（执行成功后写入、重复请求时回放）。
 * 拆成两个接口是为了让只需要拒绝重复的调用方（例如 MQ 消费幂等）不必承担存储响应体的成本。</p>
 *
 * <p>实现必须保证 key 在 {@code ttl} 之后自动过期，否则响应快照会无限增长。</p>
 */
public interface IdempotentRecordStore {

    /**
     * 查找指定 key 的首次响应快照。
     *
     * @param key 幂等 key
     * @return 存在且未过期时返回快照，否则返回空
     */
    Optional<IdempotentRecord> find(String key);

    /**
     * 保存首次响应快照，已存在时覆盖。
     *
     * @param key 幂等 key
     * @param record 响应快照
     * @param ttl 快照有效期，必须为正
     */
    void save(String key, IdempotentRecord record, Duration ttl);

    /**
     * 删除响应快照。
     *
     * @param key 幂等 key
     */
    void remove(String key);
}
