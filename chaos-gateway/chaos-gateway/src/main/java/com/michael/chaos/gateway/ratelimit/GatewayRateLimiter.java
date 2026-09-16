package com.michael.chaos.gateway.ratelimit;

import com.michael.chaos.core.ratelimit.RateLimitContext;
import reactor.core.publisher.Mono;

/**
 * Gateway 响应式限流端口。
 *
 * <p>限流上下文与算法 SPI 复用 chaos-core 的 {@link RateLimitContext} / {@code RateLimiter}：
 * 同一个 Redis 限流实现可以同时服务 Servlet 应用与网关，默认实现通过 {@link RateLimiterGatewayAdapter}
 * 把阻塞式 {@code RateLimiter} 适配为 {@link Mono}。需要原生响应式实现（如 Reactive Redis）时直接实现本接口。</p>
 */
@FunctionalInterface
public interface GatewayRateLimiter {

    /**
     * 尝试获取限流许可。
     *
     * @param context 限流上下文
     * @return 允许通过时返回 {@code true}
     */
    Mono<Boolean> tryAcquire(RateLimitContext context);
}
