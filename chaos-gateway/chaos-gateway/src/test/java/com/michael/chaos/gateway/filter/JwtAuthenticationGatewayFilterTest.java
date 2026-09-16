package com.michael.chaos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.audit.AuditEvent;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.trace.RequestTiming;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Gateway JWT 鉴权过滤器测试。
 */
class JwtAuthenticationGatewayFilterTest {

    /**
     * 开启 JWT 校验但没有 decoder 时必须拒绝请求。
     */
    @Test
    void shouldDenyWhenValidationEnabledWithoutDecoder() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        JwtAuthenticationGatewayFilter filter = new JwtAuthenticationGatewayFilter(properties, null, null);
        MockServerWebExchange exchange = exchangeWithBearer();

        StepVerifier.create(filter.filter(exchange, next -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseBody(exchange)).contains("\"code\":\"401\"");
    }

    /**
     * 鉴权拒绝时应发布网关审计事件。
     */
    @Test
    void shouldPublishAuditEventWhenDenied() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        CapturingAuditEventPublisher publisher = new CapturingAuditEventPublisher();
        JwtAuthenticationGatewayFilter filter = new JwtAuthenticationGatewayFilter(properties, null, null, publisher);
        MockServerWebExchange exchange = exchangeWithBearer();

        StepVerifier.create(filter.filter(exchange, next -> Mono.empty())).verifyComplete();

        assertThat(publisher.events()).hasSize(1);
        assertThat(publisher.events().getFirst().action()).isEqualTo("gateway.auth.denied");
        assertThat(publisher.events().getFirst().uri()).isEqualTo("/api/orders");
    }

    /**
     * JWT 已撤销时必须拒绝请求。
     */
    @Test
    void shouldDenyRevokedJwt() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        ReactiveJwtDecoder decoder = token -> Mono.just(jwt());
        JwtRevocationService revocationService = new JwtRevocationService() {
            @Override
            public boolean isRevoked(String tokenId) {
                return true;
            }

            @Override
            public void revoke(String tokenId, Duration ttl) {
            }
        };
        JwtAuthenticationGatewayFilter filter = new JwtAuthenticationGatewayFilter(properties, decoder, revocationService);
        MockServerWebExchange exchange = exchangeWithBearer();

        StepVerifier.create(filter.filter(exchange, next -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseBody(exchange)).contains("\"message\":\"unauthorized\"");
    }

    @Test
    void shouldRecordOnlyAuthenticationBeforeForwardingCompletes() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getJwt().setRevocationCheckEnabled(false);
        ReactiveJwtDecoder decoder = token -> Mono.just(jwt());
        JwtAuthenticationGatewayFilter filter = new JwtAuthenticationGatewayFilter(properties, decoder, null);
        MockServerWebExchange exchange = exchangeWithBearer();
        RequestTiming timing = RequestTiming.start();
        exchange.getAttributes().put(RequestTiming.ATTRIBUTE_NAME, timing);

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.never()))
                .then(() -> assertThat(timing.snapshot().stageCounts()).containsEntry("auth", 1L))
                .thenCancel()
                .verify();
    }

    /**
     * token 不含 userId/tenantId claim 时，客户端伪造的身份请求头不能透传给下游。
     */
    @Test
    void shouldNotForwardSpoofedIdentityHeadersWhenClaimsMissing() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getJwt().setRevocationCheckEnabled(false);
        ReactiveJwtDecoder decoder = token -> Mono.just(jwt());
        JwtAuthenticationGatewayFilter filter = new JwtAuthenticationGatewayFilter(properties, decoder, null);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .header("X-User-Id", "1")
                .header("X-Tenant-Id", "tenant-b"));

        StepVerifier.create(filter.filter(exchange, next -> {
            assertThat(next.getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
            assertThat(next.getRequest().getHeaders().containsKey("X-Tenant-Id")).isFalse();
            return Mono.empty();
        })).verifyComplete();
    }

    /**
     * token 含身份 claim 时以 claim 覆盖客户端请求头，并写入 exchange attribute 供后续过滤器使用。
     */
    @Test
    void shouldOverrideIdentityHeadersWithClaims() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getJwt().setRevocationCheckEnabled(false);
        ReactiveJwtDecoder decoder = token -> Mono.just(new Jwt(
                "access-token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                java.util.Map.of("alg", "RS256"),
                java.util.Map.of("sub", "1001", "userId", "1001", "tenantId", "tenant-a")
        ));
        JwtAuthenticationGatewayFilter filter = new JwtAuthenticationGatewayFilter(properties, decoder, null);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .header("X-User-Id", "1"));

        StepVerifier.create(filter.filter(exchange, next -> {
            assertThat(next.getRequest().getHeaders().get("X-User-Id")).containsExactly("1001");
            assertThat(next.getRequest().getHeaders().getFirst("X-Tenant-Id")).isEqualTo("tenant-a");
            assertThat(GatewayExchangeAttributes.authenticatedTenantId(next)).isEqualTo("tenant-a");
            return Mono.empty();
        })).verifyComplete();
    }

    /**
     * 关闭网关验签时不能透传任何客户端身份请求头。
     */
    @Test
    void shouldStripIdentityHeadersWhenValidationDisabled() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getJwt().setValidationEnabled(false);
        JwtAuthenticationGatewayFilter filter = new JwtAuthenticationGatewayFilter(properties, null, null);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .header("X-User-Id", "1"));

        StepVerifier.create(filter.filter(exchange, next -> {
            assertThat(next.getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
            return Mono.empty();
        })).verifyComplete();
    }

    private MockServerWebExchange exchangeWithBearer() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"));
    }

    private Jwt jwt() {
        return new Jwt(
                "access-token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                java.util.Map.of("alg", "none"),
                java.util.Map.of("sub", "1001")
        );
    }

    private String responseBody(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }

    private static final class CapturingAuditEventPublisher implements AuditEventPublisher {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void publish(AuditEvent event) {
            events.add(event);
        }

        private List<AuditEvent> events() {
            return events;
        }
    }
}
