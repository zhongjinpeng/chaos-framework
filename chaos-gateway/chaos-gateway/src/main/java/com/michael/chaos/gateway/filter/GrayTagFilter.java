package com.michael.chaos.gateway.filter;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 灰度标签透传过滤器。
 */
public class GrayTagFilter implements GlobalFilter, Ordered {

    private final ChaosGatewayProperties properties;

    /**
     * 创建灰度标签过滤器。
     */
    public GrayTagFilter(ChaosGatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * 灰度启用且存在灰度标签时，将标签继续透传给下游服务。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isGrayEnabled()) {
            return chain.filter(exchange);
        }
        String grayTag = exchange.getRequest().getHeaders().getFirst(ChaosHeaders.GRAY_TAG);
        if (grayTag == null || grayTag.isBlank()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(ChaosHeaders.GRAY_TAG, grayTag)
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /**
     * 返回过滤器执行顺序。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 25;
    }
}
