package com.chaos.security.context;

import com.chaos.core.context.RequestContext;
import com.chaos.core.context.RequestContextAttributes;
import com.chaos.core.context.RequestContextSnapshot;
import com.chaos.security.auth.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 将认证用户信息同步到框架请求上下文。
 *
 * <p>资源服务器完成 JWT 认证后，后续 MyBatis 多租户、审计字段、日志等能力可以统一从
 * {@link RequestContext} 或 {@link SecurityUtils} 读取用户与租户信息。</p>
 *
 * <p>请求结束后必须恢复进入过滤器前的上下文：Tomcat 线程会被复用，如果外层没有 {@code TraceFilter}
 * 负责清理（例如关闭了 {@code chaos.web.trace-enabled}），上一个用户的 userId/tenantId 会泄漏给下一个请求。</p>
 */
public class SecurityContextRequestFilter extends OncePerRequestFilter {

    /**
     * 当前过滤器只补充用户与租户信息，不负责认证。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Optional<RequestContextSnapshot> previous = RequestContext.current();
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        try {
            SecurityUtils.currentUser().ifPresent(user -> {
                RequestContext.setUserId(user.userId());
                RequestContext.setTenantId(user.tenantId());
                request.setAttribute(RequestContextAttributes.USER_ID, user.userId());
                request.setAttribute(RequestContextAttributes.TENANT_ID, user.tenantId());
                putIfNotBlank("userId", user.userId());
                putIfNotBlank("tenantId", user.tenantId());
            });
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
            if (previousMdc != null && !previousMdc.isEmpty()) {
                MDC.setContextMap(previousMdc);
            }
            if (previous.isPresent()) {
                RequestContext.set(previous.get());
            } else {
                RequestContext.clear();
            }
        }
    }

    private static void putIfNotBlank(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }
}
