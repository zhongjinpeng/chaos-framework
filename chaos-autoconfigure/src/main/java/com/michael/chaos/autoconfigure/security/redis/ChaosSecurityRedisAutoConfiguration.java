package com.michael.chaos.autoconfigure.security.redis;

import com.michael.chaos.autoconfigure.authorization.ChaosAuthorizationAutoConfiguration;
import com.michael.chaos.autoconfigure.gateway.ChaosGatewayAutoConfiguration;
import com.michael.chaos.autoconfigure.security.ChaosSecurityAutoConfiguration;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.redis.token.RedisJwtRevocationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 安全领域 Redis 实现装配：JWT 黑名单。
 *
 * <p>实现类位于 chaos-security-redis，本类只负责装配。条件：classpath 同时存在 chaos-security-redis
 * （由 security / gateway starter 带入）与 Spring Data Redis（由 chaos-redis-starter 带入），且容器中已有
 * {@link StringRedisTemplate}。资源服务器、网关与授权服务器因此共用同一个 {@link RedisJwtRevocationService}，
 * 保证授权服务器写入的撤销记录对另外两方可见。</p>
 *
 * <p>必须先于安全、授权服务器和网关自动装配执行，否则它们的 {@code @ConditionalOnMissingBean} 会先注册
 * {@code NoopJwtRevocationService}；同时必须晚于 Redis 连接相关自动装配，才能看到 {@link StringRedisTemplate}。</p>
 */
@AutoConfiguration(
        afterName = {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "org.redisson.spring.starter.RedissonAutoConfigurationV2"
        },
        beforeName = {
                "com.michael.chaos.autoconfigure.security.ChaosSecurityAutoConfiguration",
                "com.michael.chaos.autoconfigure.authorization.ChaosAuthorizationAutoConfiguration",
                "com.michael.chaos.autoconfigure.gateway.ChaosGatewayAutoConfiguration"
        })
@ConditionalOnClass({RedisJwtRevocationService.class, StringRedisTemplate.class})
public class ChaosSecurityRedisAutoConfiguration {

    /**
     * 注册 Redis JWT 黑名单撤销服务。
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(JwtRevocationService.class)
    public JwtRevocationService jwtRevocationService(StringRedisTemplate stringRedisTemplate) {
        return new RedisJwtRevocationService(stringRedisTemplate);
    }
}
