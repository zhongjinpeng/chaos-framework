package com.michael.chaos.gateway.filter;

import com.michael.chaos.core.net.CidrMatcher;
import com.michael.chaos.core.net.ForwardedClientIpResolver;
import java.net.InetSocketAddress;
import java.util.List;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * Gateway 客户端 IP 解析适配器。
 *
 * <p>解析规则统一由 chaos-core 的 {@link ForwardedClientIpResolver} 实现，与下游 Servlet 服务完全一致；
 * 本类只负责从 {@link ServerWebExchange} 取出直连地址与转发头，并把结果缓存到 exchange attribute，
 * 保证审计日志、黑名单和限流看到的是同一个 IP。</p>
 *
 * <p>网关可信代理可能通过配置中心动态刷新，因此使用 {@link CidrMatcher#lenient} 跳过非法条目，
 * 并按配置内容缓存解析器，避免每个请求重复解析 CIDR。</p>
 */
public final class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private static final String X_REAL_IP = "X-Real-IP";

    private static final String ATTR_KEY = ClientIpResolver.class.getName() + ".clientIp";

    private static volatile CachedResolver cachedResolver = new CachedResolver(List.of(), new ForwardedClientIpResolver());

    private ClientIpResolver() {
    }

    /**
     * 解析客户端 IP，不信任任何转发头。
     *
     * <p>若同一请求已经通过 {@link #resolve(ServerWebExchange, List)} 解析过，则复用缓存结果。</p>
     */
    public static String resolve(ServerWebExchange exchange) {
        String cached = exchange.getAttribute(ATTR_KEY);
        if (cached != null) {
            return cached;
        }
        return remoteAddress(exchange.getRequest());
    }

    /**
     * 按可信代理列表解析真实客户端 IP，并缓存到 exchange attribute。
     *
     * @param exchange 当前请求
     * @param trustedProxies 可信代理（精确 IP 或 CIDR）
     * @return 客户端 IP；无法确定时返回空字符串
     */
    public static String resolve(ServerWebExchange exchange, List<String> trustedProxies) {
        String cached = exchange.getAttribute(ATTR_KEY);
        if (cached != null) {
            return cached;
        }
        ServerHttpRequest request = exchange.getRequest();
        String resolved = resolver(trustedProxies).resolve(
                remoteAddress(request),
                request.getHeaders().getFirst(X_FORWARDED_FOR),
                request.getHeaders().getFirst(X_REAL_IP));
        exchange.getAttributes().put(ATTR_KEY, resolved);
        return resolved;
    }

    private static ForwardedClientIpResolver resolver(List<String> trustedProxies) {
        List<String> key = trustedProxies == null ? List.of() : trustedProxies;
        CachedResolver current = cachedResolver;
        if (current.trustedProxies().equals(key)) {
            return current.resolver();
        }
        CachedResolver refreshed = new CachedResolver(List.copyOf(key),
                new ForwardedClientIpResolver(CidrMatcher.lenient(key)));
        cachedResolver = refreshed;
        return refreshed.resolver();
    }

    private static String remoteAddress(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress == null || remoteAddress.getAddress() == null
                ? ""
                : remoteAddress.getAddress().getHostAddress();
    }

    private record CachedResolver(List<String> trustedProxies, ForwardedClientIpResolver resolver) {
    }
}
