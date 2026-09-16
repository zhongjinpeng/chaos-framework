package com.michael.chaos.domain.cache;

/**
 * 生成稳定缓存 key 的策略。
 *
 * <p>业务代码需要生成符合框架约定的缓存名称时，应依赖该抽象，
 * 避免在业务逻辑中直接拼接 Redis key。</p>
 */
public interface CacheKeyStrategy {

    /**
     * 根据命名空间和业务 key 构建缓存 key。
     */
    String build(String namespace, String key);
}
