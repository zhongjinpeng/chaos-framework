package com.michael.chaos.gateway.security;

import com.michael.chaos.security.api.token.TokenIntrospectionCache;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import reactor.core.publisher.Mono;

/**
 * 带超时与短期缓存的 opaque token introspector 装饰器。
 *
 * <p>未加缓存时网关每个请求都会远程调用授权服务器，授权服务器变慢会直接拖垮网关连接池；
 * 未设超时时一次慢调用会一直占用连接。本装饰器：</p>
 * <ul>
 *     <li>对 introspection 调用设置整体超时；</li>
 *     <li>成功结果缓存策略由 chaos-security-api 的 {@link TokenIntrospectionCache} 统一实现，与资源服务器侧一致。</li>
 * </ul>
 *
 * <p>一致性说明：token 被撤销后，最多在 {@code cacheTtl} 时间内仍可能通过网关校验。</p>
 */
public class CachingReactiveOpaqueTokenIntrospector implements ReactiveOpaqueTokenIntrospector {

    private final ReactiveOpaqueTokenIntrospector delegate;

    private final Duration timeout;

    private final TokenIntrospectionCache<OAuth2AuthenticatedPrincipal> cache;

    /**
     * 创建带缓存和超时的 introspector。
     *
     * @param delegate 实际执行远程 introspection 的实现
     * @param cacheTtl 缓存时长，0 或负数表示不缓存
     * @param maxSize 缓存最大条目数
     * @param timeout 远程调用超时
     */
    public CachingReactiveOpaqueTokenIntrospector(
            ReactiveOpaqueTokenIntrospector delegate,
            Duration cacheTtl,
            int maxSize,
            Duration timeout) {
        this(delegate, cacheTtl, maxSize, timeout, Clock.systemUTC());
    }

    CachingReactiveOpaqueTokenIntrospector(
            ReactiveOpaqueTokenIntrospector delegate,
            Duration cacheTtl,
            int maxSize,
            Duration timeout,
            Clock clock) {
        this.delegate = Objects.requireNonNull(delegate);
        this.timeout = timeout;
        this.cache = new TokenIntrospectionCache<>(cacheTtl, maxSize, clock);
    }

    @Override
    public Mono<OAuth2AuthenticatedPrincipal> introspect(String token) {
        Optional<OAuth2AuthenticatedPrincipal> cached = cache.get(token);
        if (cached.isPresent()) {
            return Mono.just(cached.get());
        }
        Mono<OAuth2AuthenticatedPrincipal> result = delegate.introspect(token);
        if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
            result = result.timeout(timeout);
        }
        if (!cache.enabled()) {
            return result;
        }
        return result.doOnNext(principal -> cache.put(token, principal, expiresAt(principal)));
    }

    /**
     * 返回当前缓存条目数，仅用于测试和监控。
     */
    int cacheSize() {
        return cache.size();
    }

    private static Instant expiresAt(OAuth2AuthenticatedPrincipal principal) {
        Object exp = principal.getAttributes().get(OAuth2TokenIntrospectionClaimNames.EXP);
        return exp instanceof Instant instant ? instant : null;
    }
}
