package com.michael.chaos.autoconfigure.authorization;

import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.authorization.captcha.CaptchaStore;
import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import com.michael.chaos.authorization.kickout.AuthorizationKickoutService;
import com.michael.chaos.authorization.kickout.AuthorizationSessionRegistry;
import com.michael.chaos.authorization.password.LoginFailureLimiter;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.redis.authorization.RedisAuthorizationKickoutService;
import com.michael.chaos.security.redis.authorization.RedisAuthorizationSessionRegistry;
import com.michael.chaos.security.redis.authorization.RedisCaptchaStore;
import com.michael.chaos.security.redis.authorization.RedisLoginFailureLimiter;
import com.michael.chaos.security.redis.authorization.RedisOAuth2AuthorizationConsentService;
import com.michael.chaos.security.redis.authorization.RedisOAuth2AuthorizationService;
import com.michael.chaos.security.redis.authorization.RedisRegisteredClientRepository;
import com.michael.chaos.security.redis.token.RedisJwtRevocationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.ResolvableType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.ClassUtils;

/**
 * 授权服务器 Redis 存储的创建入口。
 *
 * <p>spring-data-redis 与 chaos-security-redis 都是 chaos-autoconfigure 的可选依赖。所有引用 Redis 类型的代码
 * 集中在本类，{@link ChaosAuthorizationAutoConfiguration} 只在 {@link #isPresent(ClassLoader)} 为真时调用这里的方法；
 * JVM 按需解析方法调用，因此缺少 Redis 依赖的应用加载自动装配类时不会触发 {@code NoClassDefFoundError}。</p>
 *
 * <p>注意：自动装配的 {@code @Bean} 方法签名（包括 {@code ObjectProvider<RedisTemplate<...>>} 这样的泛型参数）
 * 会被 Spring 反射解析，不能直接出现可选类型。</p>
 */
final class AuthorizationRedisStores {

    private AuthorizationRedisStores() {
    }

    static boolean isPresent(ClassLoader classLoader) {
        return ClassUtils.isPresent("org.springframework.data.redis.core.RedisTemplate", classLoader)
                && ClassUtils.isPresent("com.michael.chaos.security.redis.authorization.RedisOAuth2AuthorizationService", classLoader);
    }

    static boolean hasStringRedisTemplate(BeanFactory beanFactory) {
        return beanFactory.getBeanProvider(StringRedisTemplate.class).getIfAvailable() != null;
    }

    static boolean hasObjectRedisTemplate(BeanFactory beanFactory) {
        return objectRedisTemplate(beanFactory) != null;
    }

    static LoginFailureLimiter loginFailureLimiter(BeanFactory beanFactory, ChaosAuthorizationProperties.LoginLock loginLock) {
        return new RedisLoginFailureLimiter(
                beanFactory.getBean(StringRedisTemplate.class),
                loginLock.getRedisKeyPrefix(),
                loginLock.getMaxFailures(),
                loginLock.getLockDuration()
        );
    }

    static CaptchaStore captchaStore(BeanFactory beanFactory, ChaosAuthorizationProperties properties) {
        return new RedisCaptchaStore(beanFactory.getBean(StringRedisTemplate.class), properties.getCaptcha().getRedisKeyPrefix());
    }

    static RegisteredClientRepository registeredClientRepository(
            BeanFactory beanFactory,
            ChaosAuthorizationProperties properties,
            RegisteredClient defaultClient) {
        RedisRegisteredClientRepository repository =
                new RedisRegisteredClientRepository(requireObjectRedisTemplate(beanFactory, "Redis client store mode"), properties);
        repository.save(defaultClient);
        return repository;
    }

    static OAuth2AuthorizationService authorizationService(BeanFactory beanFactory, ChaosAuthorizationProperties properties) {
        return new RedisOAuth2AuthorizationService(requireObjectRedisTemplate(beanFactory, "Redis token mode"), properties);
    }

    static OAuth2AuthorizationConsentService authorizationConsentService(
            BeanFactory beanFactory,
            ChaosAuthorizationProperties properties) {
        return new RedisOAuth2AuthorizationConsentService(
                requireObjectRedisTemplate(beanFactory, "Redis authorization consent mode"), properties);
    }

    static JwtRevocationService jwtRevocationService(BeanFactory beanFactory) {
        return new RedisJwtRevocationService(beanFactory.getBean(StringRedisTemplate.class));
    }

    static AuthorizationSessionRegistry sessionRegistry(BeanFactory beanFactory, ChaosAuthorizationProperties properties) {
        return new RedisAuthorizationSessionRegistry(
                requireObjectRedisTemplate(beanFactory, "Redis authorization session registry"), properties);
    }

    /**
     * Redis token 模式下的互踢服务；授权存储不是 Redis 实现时返回 {@code null}，由调用方回退到通用实现。
     */
    static AuthorizationKickoutService kickoutService(
            OAuth2AuthorizationService authorizationService,
            ChaosAuthorizationProperties properties,
            JwtRevocationService jwtRevocationService,
            AuditEventPublisher auditEventPublisher) {
        if (authorizationService instanceof RedisOAuth2AuthorizationService redisAuthorizationService) {
            return new RedisAuthorizationKickoutService(
                    redisAuthorizationService,
                    properties,
                    jwtRevocationService,
                    auditEventPublisher
            );
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static RedisTemplate<Object, Object> objectRedisTemplate(BeanFactory beanFactory) {
        ResolvableType type = ResolvableType.forClassWithGenerics(RedisTemplate.class, Object.class, Object.class);
        return (RedisTemplate<Object, Object>) beanFactory.getBeanProvider(type).getIfAvailable();
    }

    private static RedisTemplate<Object, Object> requireObjectRedisTemplate(BeanFactory beanFactory, String mode) {
        RedisTemplate<Object, Object> redisTemplate = objectRedisTemplate(beanFactory);
        if (redisTemplate == null) {
            throw new IllegalStateException(mode + " requires a RedisTemplate bean");
        }
        return redisTemplate;
    }
}
