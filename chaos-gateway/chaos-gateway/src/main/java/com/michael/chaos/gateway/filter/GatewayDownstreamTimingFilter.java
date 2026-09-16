package com.michael.chaos.gateway.filter;

import com.michael.chaos.trace.RequestTiming;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Measures the route/downstream part of a Gateway request separately from authentication.
 */
public class GatewayDownstreamTimingFilter implements GlobalFilter, Ordered {

    private static final int BEFORE_NETTY_ROUTING_ORDER = 9_999;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        RequestTiming timing = exchange.getAttribute(RequestTiming.ATTRIBUTE_NAME);
        if (timing == null) {
            return chain.filter(exchange);
        }
        return GatewayRequestTimingSupport.time(chain.filter(exchange), timing, "downstream");
    }

    @Override
    public int getOrder() {
        return BEFORE_NETTY_ROUTING_ORDER;
    }
}
