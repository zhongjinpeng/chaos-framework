package com.michael.chaos.autoconfigure.security.redis;

import com.michael.chaos.autoconfigure.authorization.ChaosAuthorizationAutoConfiguration;
import com.michael.chaos.autoconfigure.gateway.ChaosGatewayAutoConfiguration;
import com.michael.chaos.autoconfigure.security.ChaosSecurityAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.michael.chaos.security.api.access.AuthorizationPolicyJsonCodec;
import com.michael.chaos.security.api.access.AuthorizationPolicySource;
import com.michael.chaos.security.api.access.CachingAuthorizationPolicySource;
import com.michael.chaos.security.api.access.CompositeAuthorizationPolicy;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.config.ChaosSecurityProperties;
import com.michael.chaos.security.redis.access.RedisAuthorizationPolicySource;
import com.michael.chaos.security.redis.token.RedisJwtRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
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

    /**
     * Redis 动态策略来源。
     *
     * <p>单独放在嵌套配置里：本类同时被网关和授权服务器使用，它们的 classpath 上没有 chaos-security，
     * 因此引用 {@link ChaosSecurityProperties} 的 Bean 方法必须先确认该类存在。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ChaosSecurityProperties.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "chaos.security.access", name = "policy-source", havingValue = "redis")
    @EnableConfigurationProperties(ChaosSecurityProperties.class)
    public static class AccessPolicySourceConfiguration {

        private static final Logger log = LoggerFactory.getLogger(AccessPolicySourceConfiguration.class);

        /**
         * 注册带本地缓存的 Redis 策略来源。
         *
         * <p>拉取失败时保留上一份快照并打告警日志：策略是拒绝规则的载体，让它因为一次网络抖动而清空，
         * 等于悄悄放行。</p>
         */
        @Bean
        @ConditionalOnBean(StringRedisTemplate.class)
        @ConditionalOnMissingBean(AuthorizationPolicySource.class)
        public CachingAuthorizationPolicySource redisAuthorizationPolicySource(
                StringRedisTemplate stringRedisTemplate,
                ChaosSecurityProperties properties) {
            ChaosSecurityProperties.Access access = properties.getAccess();
            RedisAuthorizationPolicySource source = new RedisAuthorizationPolicySource(
                    stringRedisTemplate,
                    access.getPolicyRedisKey(),
                    new AuthorizationPolicyJsonCodec());
            return new CachingAuthorizationPolicySource(
                    source,
                    access.getPolicyCacheTtl(),
                    failure -> log.warn(
                            "读取 Redis 授权策略失败（key={}），继续使用上一份策略快照：{}",
                            source.key(),
                            failure.getMessage()));
        }

        /**
         * 把动态策略接入顶层策略列表。
         */
        @Bean
        @ConditionalOnBean(AuthorizationPolicySource.class)
        @ConditionalOnMissingBean(name = "chaosRedisAccessPolicy")
        public CompositeAuthorizationPolicy chaosRedisAccessPolicy(
                AuthorizationPolicySource authorizationPolicySource,
                ChaosSecurityProperties properties) {
            return new CompositeAuthorizationPolicy(
                    "chaos-redis-access",
                    authorizationPolicySource,
                    properties.getAccess().getCombiningAlgorithm());
        }
    }
}
