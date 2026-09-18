package com.michael.chaos.autoconfigure.security.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.michael.chaos.autoconfigure.authorization.ChaosAuthorizationAutoConfiguration;
import com.michael.chaos.autoconfigure.gateway.ChaosGatewayAutoConfiguration;
import com.michael.chaos.autoconfigure.security.ChaosSecurityAutoConfiguration;
import com.michael.chaos.security.api.access.AuthorizationPolicySource;
import com.michael.chaos.security.api.access.CachingAuthorizationPolicySource;
import com.michael.chaos.security.api.access.CompositeAuthorizationPolicy;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.redis.token.RedisJwtRevocationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 安全领域 Redis 装配测试。
 */
class ChaosSecurityRedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosSecurityRedisAutoConfiguration.class));

    /**
     * 存在 StringRedisTemplate 时注册唯一的 Redis JWT 黑名单实现。
     */
    @Test
    void shouldRegisterRedisJwtRevocationService() {
        contextRunner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtRevocationService.class);
                    assertThat(context.getBean(JwtRevocationService.class)).isInstanceOf(RedisJwtRevocationService.class);
                });
    }

    /**
     * 没有 Redis 连接时不注册，由各安全模块回退到 Noop 实现（生产环境会被生产安全检查阻断）。
     */
    @Test
    void shouldBackOffWithoutRedisTemplate() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(JwtRevocationService.class));
    }

    /**
     * 必须先于安全、授权服务器和网关自动装配，避免 Noop 实现抢先注册。
     */
    @Test
    void shouldRunBeforeSecurityModules() {
        AutoConfiguration annotation = ChaosSecurityRedisAutoConfiguration.class.getAnnotation(AutoConfiguration.class);

        assertThat(annotation.beforeName()).contains(
                "com.michael.chaos.autoconfigure.security.ChaosSecurityAutoConfiguration",
                "com.michael.chaos.autoconfigure.authorization.ChaosAuthorizationAutoConfiguration",
                "com.michael.chaos.autoconfigure.gateway.ChaosGatewayAutoConfiguration");
        assertThat(annotation.afterName()).contains(
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration");
    }

    /**
     * 默认不启用 Redis 策略来源，避免无声多出一条策略链路。
     */
    @Test
    void shouldNotRegisterPolicySourceByDefault() {
        contextRunner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AuthorizationPolicySource.class);
                    assertThat(context).doesNotHaveBean(CompositeAuthorizationPolicy.class);
                });
    }

    /**
     * 开启 redis 策略来源后，注册带缓存的来源并把它接入顶层策略列表。
     */
    @Test
    void shouldRegisterRedisPolicySourceWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "chaos.security.access.policy-source=redis",
                        "chaos.security.access.policy-redis-key=chaos:test:policies",
                        "chaos.security.access.policy-cache-ttl=5s")
                .withBean(StringRedisTemplate.class, () -> stringRedisTemplate("[]"))
                .run(context -> {
                    assertThat(context).hasSingleBean(CachingAuthorizationPolicySource.class);
                    assertThat(context).hasSingleBean(CompositeAuthorizationPolicy.class);
                    assertThat(context.getBean(CompositeAuthorizationPolicy.class).id())
                            .isEqualTo("chaos-redis-access");
                    assertThat(context.getBean(CachingAuthorizationPolicySource.class).policies()).isEmpty();
                });
    }

    /**
     * 策略来源可以被业务侧自定义实现覆盖（例如换成数据库或配置中心）。
     */
    @Test
    void customPolicySourceShouldBackOffRedisSource() {
        contextRunner
                .withPropertyValues("chaos.security.access.policy-source=redis")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(AuthorizationPolicySource.class, () -> List::of)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CachingAuthorizationPolicySource.class);
                    assertThat(context).hasSingleBean(CompositeAuthorizationPolicy.class);
                });
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate stringRedisTemplate(String policiesJson) {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        when(operations.get(anyString())).thenReturn(policiesJson);
        return template;
    }
}
