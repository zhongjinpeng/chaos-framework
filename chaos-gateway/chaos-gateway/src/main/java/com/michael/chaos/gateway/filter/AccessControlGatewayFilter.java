package com.michael.chaos.gateway.filter;

import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.metrics.NoopChaosMetrics;
import com.michael.chaos.gateway.config.AccessRuleProperties;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.security.api.access.AccessEnvironment;
import com.michael.chaos.security.api.access.AuthorizationDecision;
import com.michael.chaos.security.api.access.AuthorizationManager;
import com.michael.chaos.security.api.access.AuthorizationRequest;
import com.michael.chaos.security.api.access.AuthorizationResource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 粗粒度鉴权过滤器。
 *
 * <p>按路径 + 方法把请求映射成一个动作，再交给 {@link AuthorizationManager} 做 RBAC/ABAC 判定，
 * 把"整类接口不该进来"的请求挡在网关（例如外网禁止调用导出接口、非管理员不得访问后台路由）。</p>
 *
 * <p>边界：网关只做粗粒度判断——它看不到请求体，也不该为了鉴权去查业务数据。字段级、数据级授权
 * （"只能改自己的订单"）仍由下游服务用 {@code @RequireAccess} 完成，两边共用同一套策略模型。</p>
 *
 * <p>没有命中任何规则的请求直接放行：网关规则是"额外加固"，不是唯一防线，避免漏配一条规则就把整个站点挡死。</p>
 */
public class AccessControlGatewayFilter implements GlobalFilter, Ordered {

    private final ChaosGatewayProperties properties;

    private final AuthorizationManager authorizationManager;

    private final AuditEventPublisher auditEventPublisher;

    private final ChaosMetrics metrics;

    /**
     * 创建粗粒度鉴权过滤器。
     */
    public AccessControlGatewayFilter(
            ChaosGatewayProperties properties,
            AuthorizationManager authorizationManager) {
        this(properties, authorizationManager, new NoopAuditEventPublisher(), null);
    }

    /**
     * 创建粗粒度鉴权过滤器。
     *
     * @param auditEventPublisher 审计事件发布器
     * @param metrics 治理指标上报端口，可为 {@code null}
     */
    public AccessControlGatewayFilter(
            ChaosGatewayProperties properties,
            AuthorizationManager authorizationManager,
            AuditEventPublisher auditEventPublisher,
            ChaosMetrics metrics) {
        this.properties = properties;
        this.authorizationManager = authorizationManager;
        this.auditEventPublisher = auditEventPublisher == null ? new NoopAuditEventPublisher() : auditEventPublisher;
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ChaosGatewayProperties.Access access = properties.getAccess();
        if (!access.isEnabled() || GatewayWhitelistMatcher.isWhitelisted(exchange, properties)) {
            return chain.filter(exchange);
        }
        Optional<AccessRuleProperties> rule = matchRule(exchange, access);
        if (rule.isEmpty()) {
            return chain.filter(exchange);
        }
        AuthorizationRequest request = request(exchange, rule.get());
        AuthorizationDecision decision = authorizationManager.decide(request);
        if (decision.allowed()) {
            return chain.filter(exchange);
        }
        return denied(exchange, request, decision);
    }

    private Optional<AccessRuleProperties> matchRule(
            ServerWebExchange exchange,
            ChaosGatewayProperties.Access access) {
        ServerHttpRequest request = exchange.getRequest();
        if (GatewayWhitelistMatcher.isAmbiguous(request)) {
            // 路径穿越/歧义编码由 GatewayRequestSanitizeFilter 处理；这里不做规则匹配，避免用错误的路径放行。
            return Optional.empty();
        }
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        return access.getRules().stream()
                .filter(rule -> rule != null && rule.getPath() != null && !rule.getPath().isBlank())
                .filter(rule -> rule.getAction() != null && !rule.getAction().isBlank())
                .filter(rule -> GatewayWhitelistMatcher.matches(rule.getPath(), path))
                .filter(rule -> methodMatches(rule, method))
                .findFirst();
    }

    private boolean methodMatches(AccessRuleProperties rule, String method) {
        Set<String> methods = rule.normalizedMethods();
        return methods.isEmpty() || methods.contains(method);
    }

    private AuthorizationRequest request(ServerWebExchange exchange, AccessRuleProperties rule) {
        ServerHttpRequest request = exchange.getRequest();
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put(AccessEnvironment.CLIENT_IP, ClientIpResolver.resolve(exchange));
        environment.put(AccessEnvironment.HTTP_METHOD, request.getMethod().name());
        environment.put(AccessEnvironment.HTTP_PATH, request.getURI().getPath());
        environment.put(AccessEnvironment.URI, request.getURI().getPath());
        String tenantId = GatewayExchangeAttributes.authenticatedTenantId(exchange);
        if (!tenantId.isBlank()) {
            environment.put(AccessEnvironment.TENANT_ID, tenantId);
        }
        return AuthorizationRequest.builder(
                        GatewayExchangeAttributes.authenticatedSubject(exchange),
                        rule.getAction().trim())
                .resource(AuthorizationResource.builder(
                                rule.getResourceType() == null ? "" : rule.getResourceType().trim())
                        .id(request.getURI().getPath())
                        .build())
                .contribute(target -> target.putAll(environment))
                .build();
    }

    private Mono<Void> denied(
            ServerWebExchange exchange,
            AuthorizationRequest request,
            AuthorizationDecision decision) {
        metrics.increment(ChaosMeterNames.SECURITY_ACCESS_DENIED, ChaosMeterNames.TAG_SOURCE, "gateway");
        auditEventPublisher.publish(AuditSupport.event(AuditAction.SECURITY_PERMISSION_DENIED, AuditOutcome.DENIED)
                .principalId(request.subject().userId())
                .tenantId(request.subject().tenantId())
                .uri(exchange.getRequest().getURI().getPath())
                .ip(ClientIpResolver.resolve(exchange))
                .reason(decision.reason().isBlank() ? "access denied" : decision.reason())
                .attributes(Map.of("action", request.action(), "policy", decision.policyId()))
                .build());
        return GatewayErrorResponseWriter.forbidden(exchange);
    }

    /**
     * 在认证过滤器之后执行：需要先拿到 token 中的角色与权限。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 35;
    }
}
