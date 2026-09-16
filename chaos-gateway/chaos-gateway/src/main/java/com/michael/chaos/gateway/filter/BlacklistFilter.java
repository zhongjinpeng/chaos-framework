package com.michael.chaos.gateway.filter;

import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.core.net.CidrMatcher;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway IP 黑名单过滤器。
 */
public class BlacklistFilter implements GlobalFilter, Ordered {

    private final ChaosGatewayProperties properties;

    private final AuditEventPublisher auditEventPublisher;

    /**
     * 创建黑名单过滤器。
     */
    public BlacklistFilter(ChaosGatewayProperties properties) {
        this(properties, new NoopAuditEventPublisher());
    }

    /**
     * 创建黑名单过滤器。
     */
    public BlacklistFilter(ChaosGatewayProperties properties, AuditEventPublisher auditEventPublisher) {
        this.properties = properties;
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * 命中精确 IP 或 CIDR 黑名单时直接返回 403。
     *
     * <p>客户端 IP 按 {@code chaos.gateway.trusted-proxies} 解析，未配置可信代理时只认直连地址，
     * 防止攻击者通过伪造 {@code X-Forwarded-For} 绕过黑名单。</p>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = ClientIpResolver.resolve(exchange, properties.getTrustedProxies());
        String matchedRule = properties.getBlacklist().stream()
                .filter(rule -> CidrMatcher.matchesAny(clientIp, List.of(rule)))
                .findFirst()
                .orElse("");
        if (!matchedRule.isBlank()) {
            auditEventPublisher.publish(AuditSupport.event(AuditAction.GATEWAY_BLACKLIST_DENIED, AuditOutcome.DENIED)
                    .uri(exchange.getRequest().getURI().getPath())
                    .ip(clientIp)
                    .reason("ip blacklist")
                    .attributes(AuditSupport.attributes("rule", matchedRule))
                    .build());
            return GatewayErrorResponseWriter.forbidden(exchange);
        }
        return chain.filter(exchange);
    }

    /**
     * 返回过滤器执行顺序。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
