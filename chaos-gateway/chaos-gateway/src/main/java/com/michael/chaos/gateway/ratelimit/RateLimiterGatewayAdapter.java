package com.michael.chaos.gateway.ratelimit;

import com.michael.chaos.core.ratelimit.RateLimitContext;
import com.michael.chaos.core.ratelimit.RateLimiter;
import java.util.Objects;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 把 chaos-core 阻塞式 {@link RateLimiter} 适配为网关响应式限流端口。
 *
 * <p>网关此前维护一套独立的限流 SPI 与内存实现，Redis 限流无法在 Servlet 应用和网关之间复用。
 * 统一到 core SPI 后，引入 chaos-redis-starter 的网关直接获得集群限流。</p>
 *
 * <p>远程实现（Redis 等）会阻塞调用线程，必须切换到 {@link Schedulers#boundedElastic()}，
 * 否则会阻塞 Netty 事件循环；纯内存实现耗时极短，可以使用 {@link Schedulers#immediate()} 避免线程切换开销。</p>
 */
public class RateLimiterGatewayAdapter implements GatewayRateLimiter {

    private final RateLimiter delegate;

    private final Scheduler scheduler;

    /**
     * 创建适配器，远程调用默认在 boundedElastic 调度器上执行。
     *
     * @param delegate 阻塞式限流器
     */
    public RateLimiterGatewayAdapter(RateLimiter delegate) {
        this(delegate, Schedulers.boundedElastic());
    }

    /**
     * 创建适配器。
     *
     * @param delegate 阻塞式限流器
     * @param scheduler 执行限流判断的调度器
     */
    public RateLimiterGatewayAdapter(RateLimiter delegate, Scheduler scheduler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
    }

    @Override
    public Mono<Boolean> tryAcquire(RateLimitContext context) {
        return Mono.fromCallable(() -> delegate.tryAcquire(context)).subscribeOn(scheduler);
    }

    /**
     * 返回被适配的限流器，便于诊断和测试。
     */
    public RateLimiter delegate() {
        return delegate;
    }
}
