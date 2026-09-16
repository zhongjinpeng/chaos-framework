package com.michael.chaos.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/**
 * 资源服务器侧 introspection 缓存测试。
 */
class CachingOpaqueTokenIntrospectorTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    /**
     * TTL 内重复校验同一个 token 只调用一次授权服务器。
     */
    @Test
    void shouldCacheSuccessfulIntrospection() {
        AtomicInteger calls = new AtomicInteger();
        OpaqueTokenIntrospector delegate = token -> {
            calls.incrementAndGet();
            return principal(NOW.plusSeconds(3600));
        };
        CachingOpaqueTokenIntrospector introspector =
                new CachingOpaqueTokenIntrospector(delegate, Duration.ofSeconds(30), 10, fixedClock(NOW));

        introspector.introspect("token-a");
        introspector.introspect("token-a");

        assertThat(calls).hasValue(1);
        assertThat(introspector.cacheSize()).isEqualTo(1);
    }

    /**
     * 失败结果不缓存，token 无效或授权服务器异常后下次请求必须重新校验。
     */
    @Test
    void shouldNotCacheFailures() {
        AtomicInteger calls = new AtomicInteger();
        OpaqueTokenIntrospector delegate = token -> {
            calls.incrementAndGet();
            throw new BadOpaqueTokenException("inactive");
        };
        CachingOpaqueTokenIntrospector introspector =
                new CachingOpaqueTokenIntrospector(delegate, Duration.ofSeconds(30), 10, fixedClock(NOW));

        assertThatThrownBy(() -> introspector.introspect("bad")).isInstanceOf(BadOpaqueTokenException.class);
        assertThatThrownBy(() -> introspector.introspect("bad")).isInstanceOf(BadOpaqueTokenException.class);

        assertThat(calls).hasValue(2);
        assertThat(introspector.cacheSize()).isZero();
    }

    /**
     * token 自身 exp 早于缓存 TTL 时，以 exp 为准，过期后重新调用授权服务器。
     */
    @Test
    void shouldNotCacheBeyondTokenExpiry() {
        AtomicInteger calls = new AtomicInteger();
        MutableClock clock = new MutableClock(NOW);
        OpaqueTokenIntrospector delegate = token -> {
            calls.incrementAndGet();
            return principal(NOW.plusSeconds(5));
        };
        CachingOpaqueTokenIntrospector introspector =
                new CachingOpaqueTokenIntrospector(delegate, Duration.ofSeconds(30), 10, clock);

        introspector.introspect("short-lived");
        clock.now = NOW.plusSeconds(6);
        introspector.introspect("short-lived");

        assertThat(calls).hasValue(2);
    }

    /**
     * cacheTtl 为 0 时关闭缓存，每次都直接委托。
     */
    @Test
    void shouldBypassCacheWhenTtlIsZero() {
        AtomicInteger calls = new AtomicInteger();
        OpaqueTokenIntrospector delegate = token -> {
            calls.incrementAndGet();
            return principal(NOW.plusSeconds(3600));
        };
        CachingOpaqueTokenIntrospector introspector =
                new CachingOpaqueTokenIntrospector(delegate, Duration.ZERO, 10, fixedClock(NOW));

        introspector.introspect("token");
        introspector.introspect("token");

        assertThat(calls).hasValue(2);
    }

    /**
     * 缓存写满且全部有效时放弃写入，但仍返回校验结果。
     */
    @Test
    void shouldStillAuthenticateWhenCacheIsFull() {
        OpaqueTokenIntrospector delegate = token -> principal(NOW.plusSeconds(3600));
        CachingOpaqueTokenIntrospector introspector =
                new CachingOpaqueTokenIntrospector(delegate, Duration.ofSeconds(30), 1, fixedClock(NOW));

        introspector.introspect("first");
        OAuth2AuthenticatedPrincipal second = introspector.introspect("second");

        assertThat(second).isNotNull();
        assertThat(introspector.cacheSize()).isEqualTo(1);
    }

    private static OAuth2AuthenticatedPrincipal principal(Instant expiresAt) {
        return new DefaultOAuth2AuthenticatedPrincipal(
                "user", Map.of("sub", "user", "exp", expiresAt), List.of());
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    /**
     * 可手动推进时间的测试时钟。
     */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
