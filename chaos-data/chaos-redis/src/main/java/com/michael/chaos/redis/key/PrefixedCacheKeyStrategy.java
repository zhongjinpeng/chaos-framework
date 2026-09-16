package com.michael.chaos.redis.key;

import com.michael.chaos.domain.cache.CacheKeyStrategy;
import java.util.Objects;

/**
 * 带 Redis key 前缀的缓存 key 策略。
 *
 * <p>包装任意 {@link CacheKeyStrategy}，在其结果前加上应用前缀，避免多个服务共用 Redis 时缓存互相覆盖。</p>
 */
public class PrefixedCacheKeyStrategy implements CacheKeyStrategy {

    private final CacheKeyStrategy delegate;

    private final RedisKeyPrefix prefix;

    /**
     * 创建带前缀的缓存 key 策略。
     */
    public PrefixedCacheKeyStrategy(CacheKeyStrategy delegate, RedisKeyPrefix prefix) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.prefix = prefix == null ? RedisKeyPrefix.none() : prefix;
    }

    /**
     * 先由委托策略生成 key，再加前缀。
     */
    @Override
    public String build(String namespace, String key) {
        return prefix.apply(delegate.build(namespace, key));
    }
}
