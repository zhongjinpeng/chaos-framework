package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * 策略来源缓存装饰器测试。
 */
class CachingAuthorizationPolicySourceTest {

    private final AtomicInteger loads = new AtomicInteger();

    private final AtomicLong now = new AtomicLong(1_000L);

    private final List<RuntimeException> failures = new ArrayList<>();

    /**
     * TTL 内应复用上一次结果。
     */
    @Test
    void shouldReuseSnapshotWithinTtl() {
        CachingAuthorizationPolicySource source = source(Duration.ofSeconds(30));

        source.policies();
        source.policies();
        now.addAndGet(29_000L);
        source.policies();

        assertThat(loads).hasValue(1);
    }

    /**
     * TTL 到期后应重新拉取。
     */
    @Test
    void shouldReloadAfterTtl() {
        CachingAuthorizationPolicySource source = source(Duration.ofSeconds(30));

        source.policies();
        now.addAndGet(30_001L);
        source.policies();

        assertThat(loads).hasValue(2);
    }

    /**
     * 手动刷新应立即失效缓存。
     */
    @Test
    void refreshShouldDropSnapshot() {
        CachingAuthorizationPolicySource source = source(Duration.ofSeconds(30));

        source.policies();
        source.refresh();
        source.policies();

        assertThat(loads).hasValue(2);
    }

    /**
     * TTL 为零或负数时不缓存。
     */
    @Test
    void shouldNotCacheWhenTtlIsNotPositive() {
        CachingAuthorizationPolicySource source = source(Duration.ZERO);

        source.policies();
        source.policies();

        assertThat(loads).hasValue(2);
        assertThat(source(null).policies()).isEmpty();
    }

    /**
     * 来源返回 null 时按空列表处理。
     */
    @Test
    void shouldTolerateNullPolicies() {
        CachingAuthorizationPolicySource source = new CachingAuthorizationPolicySource(
                () -> null,
                Duration.ofSeconds(30),
                now::get,
                failure -> {
                });

        assertThat(source.policies()).isEmpty();
    }

    /**
     * 拉取失败时应继续使用旧快照，并把失败交给 failureHandler 上报，而不是让策略凭空消失。
     */
    @Test
    void shouldServeStaleSnapshotWhenReloadFails() {
        AtomicInteger attempts = new AtomicInteger();
        AuthorizationPolicy policy = policy();
        CachingAuthorizationPolicySource source = new CachingAuthorizationPolicySource(
                () -> {
                    if (attempts.getAndIncrement() == 0) {
                        return List.of(policy);
                    }
                    throw new IllegalStateException("redis down");
                },
                Duration.ofSeconds(30),
                now::get,
                failures::add);

        assertThat(source.policies()).containsExactly(policy);
        now.addAndGet(31_000L);

        assertThat(source.policies()).containsExactly(policy);
        assertThat(failures).hasSize(1);
        assertThat(source.cachedSize()).isEqualTo(1);
    }

    /**
     * 第一次拉取就失败时没有可用快照，应原样抛出。
     */
    @Test
    void shouldPropagateFailureWhenNoSnapshotExists() {
        CachingAuthorizationPolicySource source = new CachingAuthorizationPolicySource(
                () -> {
                    throw new IllegalStateException("redis down");
                },
                Duration.ofSeconds(30),
                now::get,
                failures::add);

        assertThatThrownBy(source::policies).isInstanceOf(IllegalStateException.class);
        assertThat(source.cachedSize()).isZero();
    }

    private AuthorizationPolicy policy() {
        return new AuthorizationPolicy() {

            @Override
            public String id() {
                return "p1";
            }

            @Override
            public AuthorizationDecision decide(AuthorizationRequest request) {
                return AuthorizationDecision.abstain("n/a");
            }
        };
    }

    private CachingAuthorizationPolicySource source(Duration ttl) {
        return new CachingAuthorizationPolicySource(
                () -> {
                    loads.incrementAndGet();
                    return List.of();
                },
                ttl,
                now::get,
                failures::add);
    }
}
