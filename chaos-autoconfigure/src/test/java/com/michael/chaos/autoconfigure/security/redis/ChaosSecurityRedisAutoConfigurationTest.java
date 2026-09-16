package com.michael.chaos.autoconfigure.security.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.michael.chaos.autoconfigure.authorization.ChaosAuthorizationAutoConfiguration;
import com.michael.chaos.autoconfigure.gateway.ChaosGatewayAutoConfiguration;
import com.michael.chaos.autoconfigure.security.ChaosSecurityAutoConfiguration;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.redis.token.RedisJwtRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

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
}
