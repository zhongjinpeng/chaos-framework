package com.michael.chaos.redis.key;

/**
 * Redis key 前缀。
 *
 * <p>多个服务共用同一个 Redis 时，没有前缀的幂等 key、锁 key、限流 key 会互相冲突，例如 A 服务消费过的消息
 * 会让 B 服务直接跳过。前缀由 {@code chaos.redis.key-prefix} 配置，推荐设为 {@code ${spring.application.name}}。</p>
 *
 * <p>默认前缀为空，与旧版本 key 保持一致：修改前缀相当于切换到一批新 key，滚动发布期间新旧实例的锁不会互斥，
 * 延迟队列和布隆过滤器的存量数据也不会自动迁移，因此需要显式开启。</p>
 */
public final class RedisKeyPrefix {

    private static final RedisKeyPrefix NONE = new RedisKeyPrefix("");

    private final String prefix;

    private RedisKeyPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * 创建前缀，非空且不以冒号结尾时自动补冒号。
     */
    public static RedisKeyPrefix of(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return NONE;
        }
        String value = prefix.trim();
        return new RedisKeyPrefix(value.endsWith(":") ? value : value + ":");
    }

    /**
     * 不带前缀。
     */
    public static RedisKeyPrefix none() {
        return NONE;
    }

    /**
     * 为 key 加上前缀。
     */
    public String apply(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("redis key must not be blank");
        }
        return prefix + key;
    }

    /**
     * 返回规范化后的前缀。
     */
    public String value() {
        return prefix;
    }
}
