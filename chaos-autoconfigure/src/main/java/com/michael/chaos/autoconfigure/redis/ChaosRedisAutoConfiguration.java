package com.michael.chaos.autoconfigure.redis;

import com.michael.chaos.autoconfigure.gateway.ChaosGatewayAutoConfiguration;
import com.michael.chaos.autoconfigure.security.redis.ChaosSecurityRedisAutoConfiguration;
import com.michael.chaos.autoconfigure.web.ChaosWebAutoConfiguration;
import com.michael.chaos.domain.cache.CacheKeyStrategy;
import com.michael.chaos.domain.cache.DefaultCacheKeyStrategy;
import com.michael.chaos.core.idempotent.IdempotentRecordStore;
import com.michael.chaos.core.idempotent.IdempotentRepository;
import com.michael.chaos.core.lock.DistributedLock;
import com.michael.chaos.core.ratelimit.RateLimiter;
import com.michael.chaos.redis.bloom.BloomFilterTemplate;
import com.michael.chaos.redis.delay.DelayQueueTemplate;
import com.michael.chaos.redis.idempotent.RedissonIdempotentRecordStore;
import com.michael.chaos.redis.idempotent.RedissonIdempotentRepository;
import com.michael.chaos.redis.key.PrefixedCacheKeyStrategy;
import com.michael.chaos.redis.key.RedisKeyPrefix;
import com.michael.chaos.redis.lock.RedissonDistributedLock;
import com.michael.chaos.redis.lua.LuaScriptExecutor;
import com.michael.chaos.redis.ratelimit.RedissonRateLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redis starter 自动装配。
 *
 * <p>必须先于 Web 与 Gateway 自动装配执行：否则它们的 {@code @ConditionalOnMissingBean} 会先注册内存限流器和内存幂等存储。
 * JWT 黑名单等安全领域的 Redis 实现不在这里装配，见 {@link ChaosSecurityRedisAutoConfiguration}。</p>
 */
@AutoConfiguration(
        afterName = "org.redisson.spring.starter.RedissonAutoConfigurationV2",
        beforeName = {
                "com.michael.chaos.autoconfigure.web.ChaosWebAutoConfiguration",
                "com.michael.chaos.autoconfigure.gateway.ChaosGatewayAutoConfiguration"
        })
@ConditionalOnClass(value = RedissonClient.class, name = "com.michael.chaos.redis.lock.RedissonDistributedLock")
@EnableConfigurationProperties(ChaosRedisProperties.class)

public class ChaosRedisAutoConfiguration {

    /**
     * 注册 Redis key 前缀。
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisKeyPrefix redisKeyPrefix(ChaosRedisProperties properties) {
        return RedisKeyPrefix.of(properties.getKeyPrefix());
    }

    /**
     * 注册默认缓存 key 策略，自动带上 key 前缀。
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheKeyStrategy cacheKeyStrategy(RedisKeyPrefix redisKeyPrefix) {
        return new PrefixedCacheKeyStrategy(new DefaultCacheKeyStrategy(), redisKeyPrefix);
    }

    /**
     * 注册 Redisson 分布式锁。
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public DistributedLock distributedLock(RedissonClient redissonClient, RedisKeyPrefix redisKeyPrefix) {
        return new RedissonDistributedLock(redissonClient, redisKeyPrefix);
    }

    /**
     * 注册 Redis 幂等存储。
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public IdempotentRepository idempotentRepository(RedissonClient redissonClient, RedisKeyPrefix redisKeyPrefix) {
        return new RedissonIdempotentRepository(redissonClient, redisKeyPrefix);
    }

    /**
     * 注册 Redis 幂等响应快照存储。
     *
     * <p>不按 {@code chaos.web.idempotent.replay.enabled} 做条件：Redis 实现不占本地资源，
     * 而且 chaos-redis 不应该知道 chaos-web 的配置项。是否真的读写快照由 Web 层的开关决定。</p>
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public IdempotentRecordStore idempotentRecordStore(RedissonClient redissonClient, RedisKeyPrefix redisKeyPrefix) {
        return new RedissonIdempotentRecordStore(redissonClient, redisKeyPrefix);
    }

    /**
     * 注册 Redis 集群限流器。
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter(RedissonClient redissonClient, RedisKeyPrefix redisKeyPrefix) {
        return new RedissonRateLimiter(redissonClient, redisKeyPrefix);
    }

    /**
     * 注册布隆过滤器模板。
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public BloomFilterTemplate bloomFilterTemplate(RedissonClient redissonClient, RedisKeyPrefix redisKeyPrefix) {
        return new BloomFilterTemplate(redissonClient, redisKeyPrefix);
    }

    /**
     * 注册延迟队列模板。
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public DelayQueueTemplate delayQueueTemplate(RedissonClient redissonClient, RedisKeyPrefix redisKeyPrefix) {
        return new DelayQueueTemplate(redissonClient, redisKeyPrefix);
    }

    /**
     * 注册 Lua 脚本执行器。
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public LuaScriptExecutor luaScriptExecutor(RedissonClient redissonClient) {
        return new LuaScriptExecutor(redissonClient);
    }
}
