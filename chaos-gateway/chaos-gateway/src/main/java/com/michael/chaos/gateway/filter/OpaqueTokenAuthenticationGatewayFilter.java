package com.michael.chaos.gateway.filter;

import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.security.api.auth.ChaosJwtClaims;
import com.michael.chaos.trace.RequestTiming;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway opaque/reference token 鉴权过滤器。
 *
 * <p>该过滤器通过授权服务器 introspection 端点校验 Redis/reference token，并把用户和租户 claim
 * 透传给后端服务。</p>
 *
 * <p>错误分级：token 无效（{@link BadOpaqueTokenException}）返回 401；授权服务器超时、宕机等基础设施故障
 * 返回 503，避免把服务端故障伪装成"登录失效"导致客户端清空登录态。</p>
 */
public class OpaqueTokenAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpaqueTokenAuthenticationGatewayFilter.class);

    private final ChaosGatewayProperties properties;

    private final ReactiveOpaqueTokenIntrospector opaqueTokenIntrospector;

    private final AuditEventPublisher auditEventPublisher;

    /**
     * 创建 opaque token 鉴权过滤器。
     */
    public OpaqueTokenAuthenticationGatewayFilter(
            ChaosGatewayProperties properties,
            ReactiveOpaqueTokenIntrospector opaqueTokenIntrospector) {
        this(properties, opaqueTokenIntrospector, new NoopAuditEventPublisher());
    }

    /**
     * 创建 opaque token 鉴权过滤器。
     */
    public OpaqueTokenAuthenticationGatewayFilter(
            ChaosGatewayProperties properties,
            ReactiveOpaqueTokenIntrospector opaqueTokenIntrospector,
            AuditEventPublisher auditEventPublisher) {
        this.properties = Objects.requireNonNull(properties);
        this.opaqueTokenIntrospector = opaqueTokenIntrospector;
        this.auditEventPublisher = Objects.requireNonNull(auditEventPublisher);
    }

    /**
     * 校验非白名单请求是否携带可 introspect 的 Bearer token。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isAuthEnabled()
                || properties.getToken().getType() != ChaosGatewayProperties.TokenType.OPAQUE
                || GatewayWhitelistMatcher.isWhitelisted(exchange, properties)) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange, "missing bearer token");
        }
        if (opaqueTokenIntrospector == null) {
            return unauthorized(exchange, "opaque token introspector is missing");
        }
        String token = authorization.substring("Bearer ".length());
        RequestTiming timing = exchange.getAttribute(RequestTiming.ATTRIBUTE_NAME);
        Mono<OAuth2AuthenticatedPrincipal> introspection = GatewayRequestTimingSupport.time(
                opaqueTokenIntrospector.introspect(token), timing, "auth");
        return introspection
                .onErrorResume(ex -> {
                    if (ex instanceof BadOpaqueTokenException) {
                        return unauthorized(exchange, ex.getMessage()).then(Mono.empty());
                    }
                    LOGGER.warn("Opaque token introspection unavailable: {}", ex.toString());
                    return GatewayErrorResponseWriter.serviceUnavailable(exchange).then(Mono.empty());
                })
                .flatMap(principal -> forwardAuthenticated(exchange, principal, chain));
    }

    /**
     * 返回过滤器执行顺序。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }

    /**
     * 以 introspection 结果为准重写身份请求头，claim 缺失时不保留任何客户端同名请求头。
     */
    private Mono<Void> forwardAuthenticated(
            ServerWebExchange exchange,
            OAuth2AuthenticatedPrincipal principal,
            GatewayFilterChain chain) {
        String userId = claim(principal, ChaosJwtClaims.USER_ID);
        String tenantId = claim(principal, ChaosJwtClaims.TENANT_ID);
        if (!userId.isBlank()) {
            exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_USER_ID, userId);
        }
        if (!tenantId.isBlank()) {
            exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_TENANT_ID, tenantId);
        }
        return chain.filter(exchange.mutate()
                .request(builder -> builder.headers(headers -> {
                    headers.remove(ChaosHeaders.USER_ID);
                    headers.remove(ChaosHeaders.TENANT_ID);
                    if (!userId.isBlank()) {
                        headers.set(ChaosHeaders.USER_ID, userId);
                    }
                    if (!tenantId.isBlank()) {
                        headers.set(ChaosHeaders.TENANT_ID, tenantId);
                    }
                }))
                .build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        auditEventPublisher.publish(AuditSupport.event(AuditAction.GATEWAY_AUTH_DENIED, AuditOutcome.DENIED)
                .uri(exchange.getRequest().getURI().getPath())
                .ip(ClientIpResolver.resolve(exchange))
                .reason(reason)
                .build());
        return GatewayErrorResponseWriter.unauthorized(exchange);
    }

    private String claim(OAuth2AuthenticatedPrincipal principal, String claimName) {
        Object value = principal.getAttributes().get(claimName);
        return value == null ? "" : value.toString();
    }
}
