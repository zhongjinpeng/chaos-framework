package com.michael.chaos.web.config;

import com.michael.chaos.web.idempotent.IdempotentInterceptor;
import com.michael.chaos.web.ratelimit.RateLimitInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 扩展配置。
 *
 * <p>负责注册框架提供的幂等、限流等 HandlerInterceptor。</p>
 */
public class ChaosWebMvcConfigurer implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    private final IdempotentInterceptor idempotentInterceptor;

    private final ChaosWebProperties properties;

    /**
     * 创建 Web MVC 扩展配置。
     */
    public ChaosWebMvcConfigurer(
            RateLimitInterceptor rateLimitInterceptor,
            IdempotentInterceptor idempotentInterceptor,
            ChaosWebProperties properties) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.idempotentInterceptor = idempotentInterceptor;
        this.properties = properties;
    }

    /**
     * 注册框架拦截器。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (properties.getRateLimit().isEnabled()) {
            registry.addInterceptor(rateLimitInterceptor);
        }
        registry.addInterceptor(idempotentInterceptor);
    }
}
