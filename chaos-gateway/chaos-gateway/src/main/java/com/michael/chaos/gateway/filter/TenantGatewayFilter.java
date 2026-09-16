package com.michael.chaos.gateway.filter;

import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.tenant.TenantAccessDecision;
import com.michael.chaos.tenant.TenantAccessValidator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 租户状态治理过滤器。
 *
 * <p>该过滤器只依赖租户状态端口，不关心租户数据来自数据库、配置中心还是租户中心。</p>
 *
 * <p>租户来源优先级：已认证 token 中的租户 claim 是唯一可信来源；客户端请求头只表示"想访问的租户"。
 * 两者同时存在且不一致时直接返回 403，防止 A 租户用户通过自定义租户头冒充 B 租户。
 * 只有匿名访问（token 中没有租户）时才采信请求头中的租户，并在校验租户状态后由网关重新写入标准请求头。</p>
 */
public class TenantGatewayFilter implements GlobalFilter, Ordered {

    private final ChaosGatewayProperties properties;

    private final TenantAccessValidator tenantAccessValidator;

    private final AuditEventPublisher auditEventPublisher;

    /**
     * 创建租户状态治理过滤器。
     */
    public TenantGatewayFilter(ChaosGatewayProperties properties, TenantAccessValidator tenantAccessValidator) {
        this(properties, tenantAccessValidator, new NoopAuditEventPublisher());
    }

    /**
     * 创建租户状态治理过滤器。
     */
    public TenantGatewayFilter(
            ChaosGatewayProperties properties,
            TenantAccessValidator tenantAccessValidator,
            AuditEventPublisher auditEventPublisher) {
        this.properties = properties;
        this.tenantAccessValidator = tenantAccessValidator;
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * 校验租户状态，并把租户 ID 写入标准请求头继续透传。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.getTenant().isEnabled() || GatewayWhitelistMatcher.isWhitelisted(exchange, properties)) {
            return chain.filter(exchange);
        }
        String authenticatedTenantId = GatewayExchangeAttributes.authenticatedTenantId(exchange);
        String requestedTenantId = resolveRequestedTenantId(exchange);
        if (!authenticatedTenantId.isBlank()
                && !requestedTenantId.isBlank()
                && !authenticatedTenantId.equals(requestedTenantId)) {
            publishMismatchAudit(exchange, authenticatedTenantId, requestedTenantId);
            return GatewayErrorResponseWriter.forbidden(exchange);
        }
        String tenantId = authenticatedTenantId.isBlank() ? requestedTenantId : authenticatedTenantId;
        TenantAccessDecision decision = tenantAccessValidator.validate(tenantId, properties.getTenant().isFailClosed());
        if (!decision.allowed()) {
            publishDeniedAudit(exchange, decision);
            return GatewayErrorResponseWriter.forbidden(exchange);
        }
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(ChaosHeaders.TENANT_ID);
                    String validTenantId = decision.tenant().tenantId();
                    if (validTenantId != null && !validTenantId.isBlank()) {
                        headers.set(ChaosHeaders.TENANT_ID, validTenantId);
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /**
     * 返回过滤器执行顺序。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 35;
    }

    /**
     * 读取客户端声明的租户：先读配置的租户请求头，再回退标准租户请求头（均读取剔除前的原始值）。
     */
    private String resolveRequestedTenantId(ServerWebExchange exchange) {
        String configuredHeader = properties.getTenant().getHeaderName();
        String tenantId = GatewayExchangeAttributes.clientHeader(exchange, configuredHeader);
        if ((tenantId == null || tenantId.isBlank()) && !ChaosHeaders.TENANT_ID.equalsIgnoreCase(configuredHeader)) {
            tenantId = GatewayExchangeAttributes.clientHeader(exchange, ChaosHeaders.TENANT_ID);
        }
        return tenantId == null ? "" : tenantId.trim();
    }

    private void publishMismatchAudit(ServerWebExchange exchange, String authenticatedTenantId, String requestedTenantId) {
        auditEventPublisher.publish(AuditSupport.event(AuditAction.GATEWAY_TENANT_DENIED, AuditOutcome.DENIED)
                .tenantId(authenticatedTenantId)
                .principalId(GatewayExchangeAttributes.authenticatedUserId(exchange))
                .uri(exchange.getRequest().getURI().getPath())
                .ip(ClientIpResolver.resolve(exchange))
                .reason("tenant mismatch")
                .attributes(AuditSupport.attributes("requestedTenantId", requestedTenantId))
                .build());
    }

    private void publishDeniedAudit(ServerWebExchange exchange, TenantAccessDecision decision) {
        auditEventPublisher.publish(AuditSupport.event(AuditAction.GATEWAY_TENANT_DENIED, AuditOutcome.DENIED)
                .tenantId(decision.tenant().tenantId())
                .uri(exchange.getRequest().getURI().getPath())
                .ip(ClientIpResolver.resolve(exchange))
                .reason(decision.reason())
                .attributes(AuditSupport.attributes("tenantStatus", decision.tenant().status().name()))
                .build());
    }
}
