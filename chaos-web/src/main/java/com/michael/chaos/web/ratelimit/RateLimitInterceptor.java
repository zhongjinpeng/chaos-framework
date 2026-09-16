package com.michael.chaos.web.ratelimit;

import com.michael.chaos.core.exception.BizException;
import com.michael.chaos.core.exception.CommonErrorCode;
import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.metrics.NoopChaosMetrics;
import com.michael.chaos.core.ratelimit.RateLimitContext;
import com.michael.chaos.core.ratelimit.RateLimiter;
import com.michael.chaos.web.config.ChaosWebProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Web 限流拦截器。
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ChaosWebProperties properties;

    private final RateLimiter rateLimiter;

    private final RateLimitKeyResolver keyResolver;

    private final ChaosMetrics metrics;

    /**
     * 创建限流拦截器，不上报指标。
     */
    public RateLimitInterceptor(
            ChaosWebProperties properties,
            RateLimiter rateLimiter,
            RateLimitKeyResolver keyResolver) {
        this(properties, rateLimiter, keyResolver, NoopChaosMetrics.instance());
    }

    /**
     * 创建限流拦截器。
     *
     * @param metrics 治理指标上报端口，{@code null} 时不上报
     */
    public RateLimitInterceptor(
            ChaosWebProperties properties,
            RateLimiter rateLimiter,
            RateLimitKeyResolver keyResolver,
            ChaosMetrics metrics) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.keyResolver = keyResolver;
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
    }

    /**
     * 在 Controller 方法执行前完成限流判断。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit annotation = resolveAnnotation(handlerMethod);
        int permits = annotation == null || annotation.permitsPerSecond() <= 0
                ? properties.getRateLimit().getDefaultPermitsPerSecond()
                : annotation.permitsPerSecond();
        String key = keyResolver.resolve(request, handlerMethod);
        if (!rateLimiter.tryAcquire(new RateLimitContext(key, permits))) {
            metrics.increment(ChaosMeterNames.RATE_LIMIT_REJECTED, ChaosMeterNames.TAG_SOURCE, "web");
            throw new BizException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        return true;
    }

    /**
     * 优先读取方法级限流注解，其次读取类级限流注解。
     */
    private RateLimit resolveAnnotation(HandlerMethod handlerMethod) {
        RateLimit method = handlerMethod.getMethodAnnotation(RateLimit.class);
        return method == null ? handlerMethod.getBeanType().getAnnotation(RateLimit.class) : method;
    }
}
