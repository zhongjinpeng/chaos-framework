package com.michael.chaos.gateway.filter;

import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 入站请求净化过滤器。
 *
 * <p>在任何鉴权、租户、限流逻辑之前执行，负责三件事：</p>
 * <ol>
 *     <li>拒绝包含路径穿越或歧义编码的请求，避免白名单匹配与下游路由看到的路径不一致；</li>
 *     <li>无条件剔除 {@code X-User-Id}、{@code X-Tenant-Id} 等内部身份请求头。下游服务会信任这些请求头，
 *     如果只在认证成功且 claim 非空时覆盖，白名单路径、关闭鉴权或 token 缺少 claim 时客户端伪造的值会原样透传；</li>
 *     <li>按可信代理列表解析客户端 IP 并缓存，供黑名单、限流和审计统一使用。</li>
 * </ol>
 */
public class GatewayRequestSanitizeFilter implements GlobalFilter, Ordered {

    private final ChaosGatewayProperties properties;

    /**
     * 创建入站请求净化过滤器。
     */
    public GatewayRequestSanitizeFilter(ChaosGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (properties.isRejectAmbiguousPath() && GatewayWhitelistMatcher.isAmbiguous(exchange.getRequest())) {
            return GatewayErrorResponseWriter.badRequest(exchange);
        }
        ClientIpResolver.resolve(exchange, properties.getTrustedProxies());
        Map<String, String> stripped = new LinkedHashMap<>();
        for (String headerName : properties.getInternalHeaders()) {
            if (headerName == null || headerName.isBlank()) {
                continue;
            }
            String value = exchange.getRequest().getHeaders().getFirst(headerName);
            if (value != null) {
                stripped.put(headerName.toLowerCase(Locale.ROOT), value);
            }
        }
        exchange.getAttributes().put(GatewayExchangeAttributes.STRIPPED_HEADERS, stripped);
        if (stripped.isEmpty()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> properties.getInternalHeaders().forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /**
     * 在 trace 与访问日志之后、黑名单之前执行。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
