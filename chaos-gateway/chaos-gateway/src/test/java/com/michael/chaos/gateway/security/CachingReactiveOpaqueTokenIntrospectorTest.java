package com.michael.chaos.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 带缓存与超时的 introspector 测试。
 */
class CachingReactiveOpaqueTokenIntrospectorTest {

    /**
     * 成功结果在 TTL 内应命中缓存，不重复调用授权服务器。
     */
    @Test
    void shouldCacheSuccessfulIntrospection() {
        AtomicInteger calls = new AtomicInteger();
        ReactiveOpaqueTokenIntrospector delegate = token -> {
            calls.incrementAndGet();
            return Mono.just(new DefaultOAuth2AuthenticatedPrincipal(Map.of("sub", "1001"), null));
        };
        CachingReactiveOpaqueTokenIntrospector introspector =
                new CachingReactiveOpaqueTokenIntrospector(delegate, Duration.ofSeconds(30), 10, Duration.ofSeconds(1));

        introspector.introspect("token").block();
        introspector.introspect("token").block();

        assertThat(calls).hasValue(1);
    }

    /**
     * 失败结果不能缓存。
     */
    @Test
    void shouldNotCacheFailures() {
        AtomicInteger calls = new AtomicInteger();
        ReactiveOpaqueTokenIntrospector delegate = token -> {
            calls.incrementAndGet();
            return Mono.error(new BadOpaqueTokenException("inactive"));
        };
        CachingReactiveOpaqueTokenIntrospector introspector =
                new CachingReactiveOpaqueTokenIntrospector(delegate, Duration.ofSeconds(30), 10, Duration.ofSeconds(1));

        StepVerifier.create(introspector.introspect("token")).expectError(BadOpaqueTokenException.class).verify();
        StepVerifier.create(introspector.introspect("token")).expectError(BadOpaqueTokenException.class).verify();

        assertThat(calls).hasValue(2);
        assertThat(introspector.cacheSize()).isZero();
    }

    /**
     * token 已接近过期时缓存 TTL 不能超过 token exp。
     */
    @Test
    void shouldNotCacheBeyondTokenExpiry() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ReactiveOpaqueTokenIntrospector delegate = token -> Mono.just(new DefaultOAuth2AuthenticatedPrincipal(
                Map.of("sub", "1001", "exp", now), null));
        CachingReactiveOpaqueTokenIntrospector introspector = new CachingReactiveOpaqueTokenIntrospector(
                delegate, Duration.ofSeconds(30), 10, Duration.ofSeconds(1), Clock.fixed(now, ZoneOffset.UTC));

        introspector.introspect("token").block();

        assertThat(introspector.cacheSize()).isZero();
    }

    /**
     * 授权服务器无响应时必须在超时后失败。
     */
    @Test
    void shouldTimeoutSlowIntrospection() {
        CachingReactiveOpaqueTokenIntrospector introspector = new CachingReactiveOpaqueTokenIntrospector(
                token -> Mono.never(), Duration.ZERO, 10, Duration.ofMillis(50));

        StepVerifier.create(introspector.introspect("token")).expectError(TimeoutException.class).verify();
    }
}
