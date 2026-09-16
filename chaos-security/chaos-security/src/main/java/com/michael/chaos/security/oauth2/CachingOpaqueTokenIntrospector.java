package com.michael.chaos.security.oauth2;

import com.michael.chaos.security.api.token.TokenIntrospectionCache;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/**
 * 资源服务器侧带短期缓存的 opaque token introspector 装饰器。
 *
 * <p>未加缓存时每个业务请求都会同步调用授权服务器 introspection 端点，授权服务器抖动会直接放大为
 * 所有资源服务的延迟和线程占用。缓存策略由 chaos-security-api 的 {@link TokenIntrospectionCache} 统一实现，
 * 与网关侧 {@code CachingReactiveOpaqueTokenIntrospector} 完全一致。</p>
 *
 * <p>远程调用超时不在本类处理，由构造 delegate 时使用的 HTTP 客户端连接/读取超时保证。</p>
 */
public class CachingOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private final OpaqueTokenIntrospector delegate;

    private final TokenIntrospectionCache<OAuth2AuthenticatedPrincipal> cache;

    /**
     * 创建带缓存的 introspector。
     *
     * @param delegate 实际执行远程 introspection 的实现
     * @param cacheTtl 缓存时长，null、0 或负数表示不缓存
     * @param maxSize 缓存最大条目数
     */
    public CachingOpaqueTokenIntrospector(OpaqueTokenIntrospector delegate, Duration cacheTtl, int maxSize) {
        this(delegate, cacheTtl, maxSize, Clock.systemUTC());
    }

    CachingOpaqueTokenIntrospector(OpaqueTokenIntrospector delegate, Duration cacheTtl, int maxSize, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.cache = new TokenIntrospectionCache<>(cacheTtl, maxSize, clock);
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        Optional<OAuth2AuthenticatedPrincipal> cached = cache.get(token);
        if (cached.isPresent()) {
            return cached.get();
        }
        OAuth2AuthenticatedPrincipal principal = delegate.introspect(token);
        cache.put(token, principal, expiresAt(principal));
        return principal;
    }

    /**
     * 返回当前缓存条目数，仅用于测试和监控。
     */
    int cacheSize() {
        return cache.size();
    }

    static Instant expiresAt(OAuth2AuthenticatedPrincipal principal) {
        Object exp = principal.getAttributes().get(OAuth2TokenIntrospectionClaimNames.EXP);
        return exp instanceof Instant instant ? instant : null;
    }
}
