package com.michael.chaos.redis.idempotent;

import com.michael.chaos.core.idempotent.IdempotentRepository;
import com.michael.chaos.redis.key.RedisKeyPrefix;
import java.time.Duration;
import java.util.Objects;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

/**
 * 基于 Redisson 的幂等 key 存储。
 */
public class RedissonIdempotentRepository implements IdempotentRepository {

    private final RedissonClient redissonClient;

    private final RedisKeyPrefix keyPrefix;

    /**
     * 创建不带 key 前缀的 Redisson 幂等存储。
     */
    public RedissonIdempotentRepository(RedissonClient redissonClient) {
        this(redissonClient, RedisKeyPrefix.none());
    }

    /**
     * 创建带 key 前缀的 Redisson 幂等存储。
     */
    public RedissonIdempotentRepository(RedissonClient redissonClient, RedisKeyPrefix keyPrefix) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
        this.keyPrefix = keyPrefix == null ? RedisKeyPrefix.none() : keyPrefix;
    }

    /**
     * 使用 Redis 原子 set-if-absent 保存幂等 key。
     */
    @Override
    public boolean saveIfAbsent(String key, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("idempotent ttl must be positive");
        }
        RBucket<String> bucket = redissonClient.getBucket(keyPrefix.apply(key));
        return bucket.setIfAbsent("1", ttl);
    }

    /**
     * 删除幂等 key。
     *
     * <p>接口默认实现是空操作。Redis 实现如果不覆盖，消费失败或 HTTP 请求失败后 key 不会释放：
     * MQ 重投被静默丢弃、用户重试被拒绝，而本地内存实现却能正常释放，开发和生产行为不一致。</p>
     */
    @Override
    public void remove(String key) {
        redissonClient.getBucket(keyPrefix.apply(key)).delete();
    }
}
