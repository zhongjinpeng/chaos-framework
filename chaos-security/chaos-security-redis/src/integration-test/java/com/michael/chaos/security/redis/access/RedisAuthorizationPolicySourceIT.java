package com.michael.chaos.security.redis.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.security.api.access.AccessEffect;
import com.michael.chaos.security.api.access.AccessSubject;
import com.michael.chaos.security.api.access.AttributeOperator;
import com.michael.chaos.security.api.access.AuthorizationManager;
import com.michael.chaos.security.api.access.AuthorizationRequest;
import com.michael.chaos.security.api.access.AuthorizationResource;
import com.michael.chaos.security.api.access.CachingAuthorizationPolicySource;
import com.michael.chaos.security.api.access.CompositeAuthorizationPolicy;
import com.michael.chaos.security.api.access.ConditionDefinition;
import com.michael.chaos.security.api.access.DefaultAuthorizationManager;
import com.michael.chaos.security.api.access.PolicyCombiningAlgorithm;
import com.michael.chaos.security.api.access.PolicyDefinition;
import com.michael.chaos.security.api.access.RbacAuthorizationPolicy;
import com.michael.chaos.security.api.auth.LoginUser;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis 动态策略集成测试。
 *
 * <p>验证运维最关心的一条链路：把策略写进 Redis，缓存过期后新策略自动生效，不需要重启服务。</p>
 */
@Testcontainers
class RedisAuthorizationPolicySourceIT {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;

    private final AtomicLong clock = new AtomicLong(1_000L);

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /**
     * 策略写入 Redis 后，缓存过期即生效；DENY 策略能否决 RBAC 的放行。
     */
    @Test
    void policiesWrittenToRedisShouldTakeEffectAfterCacheExpires() {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        RedisAuthorizationPolicySource source =
                new RedisAuthorizationPolicySource(template, "chaos:it:access:policies", null);
        source.save(List.of());
        CachingAuthorizationPolicySource caching = new CachingAuthorizationPolicySource(
                source,
                Duration.ofSeconds(30),
                clock::get,
                failure -> {
                });
        AuthorizationManager manager = new DefaultAuthorizationManager(List.of(
                new RbacAuthorizationPolicy(),
                new CompositeAuthorizationPolicy("redis", caching, PolicyCombiningAlgorithm.DENY_OVERRIDES)));
        AuthorizationRequest request = AuthorizationRequest.of(
                AccessSubject.from(new LoginUser("1001", "alice", "tenant-a", Set.of("user"), Set.of("order:read"))),
                "order:read",
                AuthorizationResource.NONE,
                Map.of("network", "external"));

        assertThat(manager.isAllowed(request)).isTrue();

        source.save(List.of(PolicyDefinition.deny(
                "deny-external",
                Set.of("order:read"),
                List.of(ConditionDefinition.of("environment.network", AttributeOperator.EQ, Set.of("external"))))));

        assertThat(manager.isAllowed(request)).as("缓存未过期时仍用旧策略").isTrue();

        clock.addAndGet(31_000L);

        assertThat(manager.decide(request).effect()).isEqualTo(AccessEffect.DENY);
        assertThat(manager.decide(request).policyId()).isEqualTo("deny-external");
    }

    /**
     * Redis 里的内容被改坏时，继续使用上一份快照，不会因为一次错误编辑而放行所有请求。
     */
    @Test
    void brokenPolicyContentShouldKeepPreviousSnapshot() {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        RedisAuthorizationPolicySource source =
                new RedisAuthorizationPolicySource(template, "chaos:it:access:broken", null);
        source.save(List.of(PolicyDefinition.deny(
                "deny-external",
                Set.of("order:read"),
                List.of(ConditionDefinition.of("environment.network", AttributeOperator.EQ, Set.of("external"))))));
        CachingAuthorizationPolicySource caching = new CachingAuthorizationPolicySource(
                source,
                Duration.ofSeconds(30),
                clock::get,
                failure -> {
                });

        assertThat(caching.policies()).hasSize(1);

        template.opsForValue().set("chaos:it:access:broken", "{ this is not json");
        clock.addAndGet(31_000L);

        assertThat(caching.policies()).as("坏内容不应让策略凭空消失").hasSize(1);
    }
}
