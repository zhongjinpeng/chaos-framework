package com.michael.chaos.gateway.filter;

import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.metrics.NoopChaosMetrics;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.security.api.auth.ChaosJwtClaims;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.api.token.JwtTokenIds;
import com.michael.chaos.trace.RequestTiming;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Gateway Bearer token 鉴权过滤器。
 *
 * <p>配置 JWT decoder 时会在网关完成基础验签；资源服务器仍应完成细粒度权限控制。</p>
 */
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private final ChaosGatewayProperties properties;

    private final ReactiveJwtDecoder jwtDecoder;

    private final JwtRevocationService jwtRevocationService;

    private final AuditEventPublisher auditEventPublisher;

    private final ChaosMetrics metrics;

    /**
     * 创建 Gateway 鉴权过滤器。
     */
    public JwtAuthenticationGatewayFilter(
            ChaosGatewayProperties properties,
            ReactiveJwtDecoder jwtDecoder,
            JwtRevocationService jwtRevocationService) {
        this(properties, jwtDecoder, jwtRevocationService, new NoopAuditEventPublisher());
    }

    /**
     * 创建 Gateway 鉴权过滤器。
     */
    public JwtAuthenticationGatewayFilter(
            ChaosGatewayProperties properties,
            ReactiveJwtDecoder jwtDecoder,
            JwtRevocationService jwtRevocationService,
            AuditEventPublisher auditEventPublisher) {
        this(properties, jwtDecoder, jwtRevocationService, auditEventPublisher, null);
    }

    /**
     * 创建 Gateway 鉴权过滤器。
     *
     * @param metrics 治理指标上报端口，可为 {@code null}
     */
    public JwtAuthenticationGatewayFilter(
            ChaosGatewayProperties properties,
            ReactiveJwtDecoder jwtDecoder,
            JwtRevocationService jwtRevocationService,
            AuditEventPublisher auditEventPublisher,
            ChaosMetrics metrics) {
        this.properties = properties;
        this.jwtDecoder = jwtDecoder;
        this.jwtRevocationService = jwtRevocationService;
        this.auditEventPublisher = auditEventPublisher;
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
    }

    /**
     * 校验非白名单请求是否携带 Bearer token。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isAuthEnabled()
                || properties.getToken().getType() != ChaosGatewayProperties.TokenType.JWT
                || isWhitelisted(exchange)) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange, "missing bearer token", "missing-token");
        }
        if (!properties.getJwt().isValidationEnabled()) {
            // 关闭网关验签时只做 Bearer 形态检查，资源服务器必须自行完成验签；此时网关不能透传任何身份。
            return forwardWithoutIdentity(exchange, chain);
        }
        if (jwtDecoder == null) {
            return unauthorized(exchange, "jwt decoder is missing", "no-decoder");
        }
        String token = authorization.substring("Bearer ".length());
        RequestTiming timing = exchange.getAttribute(RequestTiming.ATTRIBUTE_NAME);
        Mono<org.springframework.security.oauth2.jwt.Jwt> authentication = jwtDecoder.decode(token)
                .flatMap(jwt -> {
                    if (!properties.getJwt().isRevocationCheckEnabled()
                            || jwtRevocationService == null) {
                        return Mono.just(jwt);
                    }
                    return Mono.fromCallable(() -> jwtRevocationService.isRevoked(
                                    JwtTokenIds.resolve(jwt.getId(), jwt.getTokenValue())))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(revoked -> {
                                if (revoked) {
                                    metrics.increment(ChaosMeterNames.SECURITY_TOKEN_REVOKED,
                                            ChaosMeterNames.TAG_SOURCE, "gateway");
                                    return Mono.error(new JwtException("jwt token has been revoked"));
                                }
                                return Mono.just(jwt);
                            });
                });
        return GatewayRequestTimingSupport.time(authentication, timing, "auth")
                .onErrorResume(JwtException.class,
                        ex -> unauthorized(exchange, ex.getMessage(), "invalid-token").then(Mono.empty()))
                .flatMap(jwt -> forwardAuthenticated(exchange, jwt, chain));
    }

    /**
     * 认证成功后以 token claim 为准重写身份请求头。
     *
     * <p>先删除再写入：即使 {@link GatewayRequestSanitizeFilter} 被业务替换或关闭，
     * token 中不含 userId/tenantId claim 时也不会把客户端自带的同名请求头透传给下游。</p>
     */
    private Mono<Void> forwardAuthenticated(
            ServerWebExchange exchange,
            org.springframework.security.oauth2.jwt.Jwt jwt,
            GatewayFilterChain chain) {
        String userId = jwt.getClaimAsString(ChaosJwtClaims.USER_ID);
        String tenantId = jwt.getClaimAsString(ChaosJwtClaims.TENANT_ID);
        putAttributeIfNotBlank(exchange, GatewayExchangeAttributes.AUTHENTICATED_USER_ID, userId);
        putAttributeIfNotBlank(exchange, GatewayExchangeAttributes.AUTHENTICATED_TENANT_ID, tenantId);
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(builder -> builder.headers(headers -> {
                    headers.remove(ChaosHeaders.USER_ID);
                    headers.remove(ChaosHeaders.TENANT_ID);
                    if (userId != null && !userId.isBlank()) {
                        headers.set(ChaosHeaders.USER_ID, userId);
                    }
                    if (tenantId != null && !tenantId.isBlank()) {
                        headers.set(ChaosHeaders.TENANT_ID, tenantId);
                    }
                }))
                .build();
        return chain.filter(mutatedExchange);
    }

    /**
     * 未完成验签时剔除身份请求头后转发，避免下游把客户端伪造的身份当作已认证身份。
     */
    private Mono<Void> forwardWithoutIdentity(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange.mutate()
                .request(builder -> builder.headers(headers -> {
                    headers.remove(ChaosHeaders.USER_ID);
                    headers.remove(ChaosHeaders.TENANT_ID);
                }))
                .build());
    }

    private void putAttributeIfNotBlank(ServerWebExchange exchange, String name, String value) {
        if (value != null && !value.isBlank()) {
            exchange.getAttributes().put(name, value);
        }
    }

    /**
     * 返回 401 响应。
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason, String metricReason) {
        // reason 来自异常消息，基数不可控，只能进审计；指标用调用点给出的固定枚举值。
        metrics.increment(ChaosMeterNames.SECURITY_AUTH_FAILED,
                ChaosMeterNames.TAG_SOURCE, "gateway",
                ChaosMeterNames.TAG_REASON, metricReason);
        auditEventPublisher.publish(AuditSupport.event(AuditAction.GATEWAY_AUTH_DENIED, AuditOutcome.DENIED)
                .uri(exchange.getRequest().getURI().getPath())
                .ip(ClientIpResolver.resolve(exchange))
                .reason(reason)
                .build());
        return GatewayErrorResponseWriter.unauthorized(exchange);
    }

    /**
     * 返回过滤器执行顺序。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }

    /**
     * 判断当前请求路径是否在白名单内。
     */
    private boolean isWhitelisted(ServerWebExchange exchange) {
        return GatewayWhitelistMatcher.isWhitelisted(exchange, properties);
    }
}
