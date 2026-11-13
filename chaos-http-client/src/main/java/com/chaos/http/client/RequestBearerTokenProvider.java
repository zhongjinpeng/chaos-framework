package com.chaos.http.client;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 从当前请求转发 Bearer 令牌；用于服务间调用保留用户身份。
 *
 * <p>只在请求线程内可用；没有当前认证时返回 {@code null}，由客户端按匿名请求发送。
 * 不缓存令牌，{@link #invalidate()} 不产生副作用。</p>
 */
public final class RequestBearerTokenProvider implements ServiceTokenProvider {

    @Override
    public String token() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return null;
        }
        HttpServletRequest request = servlet.getRequest();
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7)
                : authorization;
    }

    @Override
    public void invalidate() {
        // 转发模式没有可刷新的缓存令牌。
    }
}
