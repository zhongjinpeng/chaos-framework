package com.michael.chaos.web.filter;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.core.context.ContextPropagation;
import com.michael.chaos.trace.log.MdcKeys;
import com.michael.chaos.trace.log.MdcSupport;
import com.michael.chaos.trace.TraceContext;
import com.michael.chaos.trace.TraceHeaders;
import com.michael.chaos.web.support.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet trace 上下文过滤器。
 *
 * <p>进入应用时解析 trace 请求头，写入 {@link TraceContext} 与 MDC，并在请求结束后清理所有线程上下文。</p>
 *
 * <p>身份请求头安全模型：{@code X-User-Id} / {@code X-Tenant-Id} 默认<b>不被信任</b>。
 * 原实现无条件读取这两个请求头写入上下文，攻击者可以直接伪造身份，并通过 MyBatis 租户插件跨租户读写数据、
 * 伪造审计主体，还会经 Feign/MQ 继续传给下游。现在只有同时满足以下条件才会采纳：</p>
 * <ol>
 *     <li>显式开启 {@code chaos.web.forwarding.trust-identity-headers=true}；</li>
 *     <li>请求直连对端命中 {@code chaos.web.forwarding.trusted-proxies}（例如网关所在网段）。</li>
 * </ol>
 * <p>未满足时身份只能来自认证结果（例如 chaos-security 的 SecurityContextRequestFilter）。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceFilter.class);

    private final String appName;

    private final ClientIpResolver clientIpResolver;

    private final boolean trustIdentityHeaders;

    private final CurrentSpanProvider currentSpanProvider;

    /**
     * 创建使用安全默认值的 trace 过滤器：不信任身份请求头，客户端 IP 取直连地址。
     *
     * @param appName 当前应用名称，用于日志字段
     */
    public TraceFilter(String appName) {
        this(appName, new ClientIpResolver(), false, CurrentSpanProvider.NONE);
    }

    /**
     * 创建 trace 过滤器。
     *
     * @param appName 当前应用名称
     * @param clientIpResolver 客户端 IP 与可信代理解析器
     * @param trustIdentityHeaders 是否允许从可信代理透传的身份请求头
     * @param currentSpanProvider 外部追踪系统当前 span 提供者
     */
    public TraceFilter(
            String appName,
            ClientIpResolver clientIpResolver,
            boolean trustIdentityHeaders,
            CurrentSpanProvider currentSpanProvider) {
        this.appName = appName;
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver must not be null");
        this.trustIdentityHeaders = trustIdentityHeaders;
        this.currentSpanProvider = currentSpanProvider == null ? CurrentSpanProvider.NONE : currentSpanProvider;
        if (trustIdentityHeaders && !clientIpResolver.hasTrustedProxies()) {
            log.warn("chaos.web.forwarding.trust-identity-headers=true 但未配置 trusted-proxies，身份请求头仍会被忽略");
        }
    }

    /**
     * 初始化请求级 trace 上下文并继续执行过滤器链。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            boolean identityTrusted = trustIdentityHeaders && clientIpResolver.isTrustedProxy(request);
            String tenantId = identityTrusted ? request.getHeader(ChaosHeaders.TENANT_ID) : null;
            String userId = identityTrusted ? request.getHeader(ChaosHeaders.USER_ID) : null;
            Optional<CurrentSpanProvider.SpanIds> externalSpan = currentSpanProvider.currentSpan();
            if (externalSpan.isPresent()) {
                // 引入 Micrometer Tracing 时直接沿用其 span，避免两套 traceId 在 MDC 中互相覆盖。
                TraceContext.start(
                        externalSpan.get().traceId(),
                        externalSpan.get().spanId(),
                        null,
                        request.getHeader(ChaosHeaders.TRACESTATE),
                        request.getHeader(ChaosHeaders.BAGGAGE),
                        tenantId,
                        userId,
                        appName
                );
            } else {
                TraceContext.startServer(
                        request.getHeader(ChaosHeaders.TRACE_ID),
                        request.getHeader(ChaosHeaders.TRACEPARENT),
                        request.getHeader(ChaosHeaders.TRACESTATE),
                        request.getHeader(ChaosHeaders.BAGGAGE),
                        tenantId,
                        userId,
                        appName
                );
            }
            MdcSupport.putIfNotBlank(MdcKeys.URI, request.getRequestURI());
            MdcSupport.putIfNotBlank(MdcKeys.IP, clientIpResolver.resolve(request));
            TraceHeaders.current().forEach(response::setHeader);
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
            // 兜底清理租户、数据权限、耗时统计等其他模块注册的 ThreadLocal，防止线程复用串号。
            ContextPropagation.clearAll();
        }
    }
}
