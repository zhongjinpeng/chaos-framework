package com.michael.chaos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.trace.RequestTiming;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OpaqueTokenAuthenticationGatewayFilterTest {

    /**
     * introspection 阶段耗时要和下游转发耗时分开统计，否则看不出是授权服务器慢还是业务服务慢。
     */
    @Test
    void shouldRecordOnlyIntrospectionBeforeForwardingCompletes() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getToken().setType(ChaosGatewayProperties.TokenType.OPAQUE);
        ReactiveOpaqueTokenIntrospector introspector = token -> Mono.just(
                new DefaultOAuth2AuthenticatedPrincipal(Map.of("sub", "1001"), null));
        OpaqueTokenAuthenticationGatewayFilter filter =
                new OpaqueTokenAuthenticationGatewayFilter(properties, introspector);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"));
        RequestTiming timing = RequestTiming.start();
        exchange.getAttributes().put(RequestTiming.ATTRIBUTE_NAME, timing);

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.never()))
                .then(() -> assertThat(timing.snapshot().stageCounts()).containsEntry("auth", 1L))
                .thenCancel()
                .verify();
    }

    /**
     * token 无效时返回 401。
     */
    @Test
    void shouldReturnUnauthorizedForInactiveToken() {
        MockServerWebExchange exchange = runWith(token -> Mono.error(new BadOpaqueTokenException("inactive")));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * 授权服务器故障时返回 503，而不是把基础设施故障伪装成登录失效。
     */
    @Test
    void shouldReturnServiceUnavailableWhenIntrospectionFails() {
        MockServerWebExchange exchange = runWith(token -> Mono.error(new OAuth2IntrospectionException("down")));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * introspection 结果不含身份 claim 时，客户端伪造的身份请求头不能透传。
     */
    @Test
    void shouldNotForwardSpoofedIdentityHeaders() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getToken().setType(ChaosGatewayProperties.TokenType.OPAQUE);
        OpaqueTokenAuthenticationGatewayFilter filter = new OpaqueTokenAuthenticationGatewayFilter(
                properties, token -> Mono.just(new DefaultOAuth2AuthenticatedPrincipal(Map.of("sub", "1001"), null)));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .header("X-User-Id", "1"));

        StepVerifier.create(filter.filter(exchange, next -> {
            assertThat(next.getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
            return Mono.empty();
        })).verifyComplete();
    }

    private MockServerWebExchange runWith(ReactiveOpaqueTokenIntrospector introspector) {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getToken().setType(ChaosGatewayProperties.TokenType.OPAQUE);
        OpaqueTokenAuthenticationGatewayFilter filter =
                new OpaqueTokenAuthenticationGatewayFilter(properties, introspector);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"));
        StepVerifier.create(filter.filter(exchange, next -> Mono.empty())).verifyComplete();
        return exchange;
    }
}
