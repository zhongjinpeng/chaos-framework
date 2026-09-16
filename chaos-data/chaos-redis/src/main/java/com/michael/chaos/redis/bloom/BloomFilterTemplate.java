package com.michael.chaos.redis.bloom;

import com.michael.chaos.redis.key.RedisKeyPrefix;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

/**
 * 布隆过滤器模板。
 *
 * <p>必须先调用 {@link #create} 初始化容量和误判率。旧实现在未初始化时调用 {@link #add} 会抛出 Redisson 内部异常，
 * 当前实现给出明确提示；{@link #mightContain} 对不存在的过滤器返回 {@code false}。</p>
 */
public class BloomFilterTemplate {

    private final RedissonClient redissonClient;

    private final RedisKeyPrefix keyPrefix;

    private final Map<String, RBloomFilter<?>> filters = new ConcurrentHashMap<>();

    /**
     * 创建不带 key 前缀的布隆过滤器模板。
     */
    public BloomFilterTemplate(RedissonClient redissonClient) {
        this(redissonClient, RedisKeyPrefix.none());
    }

    /**
     * 创建带 key 前缀的布隆过滤器模板。
     */
    public BloomFilterTemplate(RedissonClient redissonClient, RedisKeyPrefix keyPrefix) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
        this.keyPrefix = keyPrefix == null ? RedisKeyPrefix.none() : keyPrefix;
    }

    /**
     * 创建或获取布隆过滤器；已存在时保留原有容量配置。
     */
    @SuppressWarnings("unchecked")
    public <T> RBloomFilter<T> create(String name, long expectedInsertions, double falseProbability) {
        return (RBloomFilter<T>) filters.computeIfAbsent(name, key -> {
            RBloomFilter<T> filter = redissonClient.getBloomFilter(keyPrefix.apply(key));
            filter.tryInit(expectedInsertions, falseProbability);
            return filter;
        });
    }

    /**
     * 判断元素是否可能存在；过滤器不存在时返回 {@code false}。
     */
    public <T> boolean mightContain(String name, T value) {
        RBloomFilter<T> filter = existing(name);
        return filter != null && filter.contains(value);
    }

    /**
     * 添加元素。
     *
     * @throws IllegalStateException 过滤器尚未通过 {@link #create} 初始化
     */
    public <T> boolean add(String name, T value) {
        RBloomFilter<T> filter = existing(name);
        if (filter == null) {
            throw new IllegalStateException("Bloom filter is not initialized, call create(...) first: " + name);
        }
        return filter.add(value);
    }

    @SuppressWarnings("unchecked")
    private <T> RBloomFilter<T> existing(String name) {
        RBloomFilter<?> cached = filters.get(name);
        if (cached != null) {
            return (RBloomFilter<T>) cached;
        }
        // 其他实例可能已经初始化过同名过滤器，此时直接复用。
        RBloomFilter<T> filter = redissonClient.getBloomFilter(keyPrefix.apply(name));
        if (!filter.isExists()) {
            return null;
        }
        filters.putIfAbsent(name, filter);
        return filter;
    }
}
