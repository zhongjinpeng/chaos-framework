package com.chaos.autoconfigure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chaos.autoconfigure.gateway.ChaosGatewayAutoConfiguration;
import com.chaos.autoconfigure.web.ChaosWebAutoConfiguration;
import com.chaos.domain.cache.CacheKeyStrategy;
import com.chaos.core.idempotent.IdempotentRepository;
import com.chaos.core.ratelimit.RateLimiter;
import com.chaos.redis.idempotent.RedissonIdempotentRepository;
import com.chaos.redis.key.RedisKeyPrefix;
import com.chaos.redis.ratelimit.RedissonRateLimiter;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Redis 自动装配测试。
 */
class ChaosRedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
            .withConfiguration(AutoConfigurations.of(
                    ChaosRedisAutoConfiguration.class,
                    ChaosWebAutoConfiguration.class
            ));

    /**
     * Redis 自动装配必须在 Redisson 之后、Web 默认实现之前执行。
     */
    @Test
    void shouldDeclareRequiredAutoConfigurationOrder() {
        AutoConfiguration annotation = ChaosRedisAutoConfiguration.class.getAnnotation(AutoConfiguration.class);

        assertThat(annotation.afterName())
                .contains("org.redisson.spring.starter.RedissonAutoConfigurationV2");
        assertThat(annotation.beforeName())
                .contains("com.chaos.autoconfigure.web.ChaosWebAutoConfiguration",
                        "com.chaos.autoconfigure.gateway.ChaosGatewayAutoConfiguration");
    }

    /**
     * key 前缀应绑定到所有 Redis 组件和缓存 key 策略。
     */
    @Test
    void shouldBindKeyPrefix() {
        contextRunner.withPropertyValues("chaos.redis.key-prefix=order-service")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RedisKeyPrefix.class).value()).isEqualTo("order-service:");
                    assertThat(context.getBean(CacheKeyStrategy.class).build("user", "1"))
                            .isEqualTo("order-service:user:1");
                });
    }

    /**
     * 生产模式应使用 Redisson 限流和幂等实现，而不是本地内存实现。
     */
    @Test
    void shouldUseRedissonImplementationsInProductionMode() {
        contextRunner.withPropertyValues("chaos.production-safety.production-mode=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RateLimiter.class);
                    assertThat(context.getBean(RateLimiter.class)).isInstanceOf(RedissonRateLimiter.class);
                    assertThat(context).hasSingleBean(IdempotentRepository.class);
                    assertThat(context.getBean(IdempotentRepository.class))
                            .isInstanceOf(RedissonIdempotentRepository.class);
                });
    }
}
