package com.michael.chaos.web.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;

/**
 * Web 限流 key 解析器。
 */
public interface RateLimitKeyResolver {

    /**
     * 根据请求和处理方法生成限流 key。
     */
    String resolve(HttpServletRequest request, HandlerMethod handlerMethod);
}
