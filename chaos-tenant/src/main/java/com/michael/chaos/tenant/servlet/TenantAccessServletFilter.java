package com.michael.chaos.tenant.servlet;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.metrics.NoopChaosMetrics;
import com.michael.chaos.tenant.TenantAccessDecision;
import com.michael.chaos.tenant.TenantAccessValidator;
import com.michael.chaos.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet 服务的租户状态校验过滤器。
 *
 * <p>原先只有网关会调用 {@link TenantAccessValidator}，直接暴露或绕过网关访问的 Servlet 服务完全不校验
 * 租户状态（冻结、过期等）。该过滤器在认证过滤器之后读取 {@link RequestContext} 中的租户 ID，
 * 校验通过后写入 {@link TenantContext}，并在请求结束时清理，避免线程复用导致串租户。</p>
 *
 * <p>租户 ID 只来自认证结果或可信代理透传，本过滤器不读取任何客户端请求头。</p>
 */
public class TenantAccessServletFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final TenantAccessValidator validator;

    private final List<String> excludePaths;

    private final ChaosMetrics metrics;

    /**
     * 创建租户状态校验过滤器，不上报指标。
     *
     * @param validator 租户访问校验器
     * @param excludePaths 不校验租户的路径（Ant 风格），例如健康检查和登录接口
     */
    public TenantAccessServletFilter(TenantAccessValidator validator, List<String> excludePaths) {
        this(validator, excludePaths, null);
    }

    /**
     * 创建租户状态校验过滤器。
     *
     * @param metrics 治理指标上报端口，可为 {@code null}
     */
    public TenantAccessServletFilter(
            TenantAccessValidator validator, List<String> excludePaths, ChaosMetrics metrics) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.excludePaths = excludePaths == null ? List.of() : List.copyOf(excludePaths);
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return excludePaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        TenantAccessDecision decision = validator.validate(RequestContext.tenantId());
        if (!decision.allowed()) {
            // 拒绝原因是低基数枚举（租户状态名或 missing），可以安全地作为标签。
            metrics.increment(ChaosMeterNames.TENANT_DENIED,
                    ChaosMeterNames.TAG_SOURCE, "servlet",
                    ChaosMeterNames.TAG_REASON, denialReason(decision));
            writeForbidden(response);
            return;
        }
        try {
            if (!decision.tenant().tenantId().isBlank()) {
                TenantContext.set(decision.tenant());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 把拒绝原因归一化为低基数标签值。
     */
    private static String denialReason(TenantAccessDecision decision) {
        String tenantId = decision.tenant().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return "missing";
        }
        return decision.tenant().status() == null
                ? "unknown"
                : decision.tenant().status().name().toLowerCase(Locale.ROOT);
    }

    /**
     * 返回统一 JSON 结构，拒绝原因不回显给调用方，避免暴露租户状态细节。
     */
    private static void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"403\",\"message\":\"tenant access denied\",\"data\":null}");
    }
}
