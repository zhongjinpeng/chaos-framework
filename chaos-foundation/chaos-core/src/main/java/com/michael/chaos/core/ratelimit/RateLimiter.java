package com.michael.chaos.core.ratelimit;

/**
 * 限流端口。
 */
public interface RateLimiter {

    /**
     * 尝试获取一次请求许可。
     *
     * @param context 限流上下文
     * @return 获取许可成功时返回 {@code true}
     */
    boolean tryAcquire(RateLimitContext context);
}
