package com.michael.chaos.domain.cache;

/**
 * 默认缓存 key 生成策略。
 */
public class DefaultCacheKeyStrategy implements CacheKeyStrategy {

    /**
     * 使用 {@code namespace:key} 形式生成缓存 key。
     */
    @Override
    public String build(String namespace, String key) {
        if (namespace == null || namespace.isBlank()) {
            return key;
        }
        return namespace + ":" + key;
    }
}
