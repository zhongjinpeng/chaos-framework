package com.chaos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chaos.core.diagnostic.ChaosDiagnosticException;
import com.chaos.gateway.config.ChaosGatewayProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class OAuth2ClientAuthenticationGatewayFilterFactoryTest {

    @Test
    void shouldReplaceCallerAuthorizationWithSelectedClientCredentials() {
        OAuth2ClientAuthenticationGatewayFilterFactory factory = factory(
                "web", "web-client", "secret:value");
        OAuth2ClientAuthenticationGatewayFilterFactory.Config config =
                new OAuth2ClientAuthenticationGatewayFilterFactory.Config();
        config.setRegistration("web");
        GatewayFilter filter = factory.apply(config);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/web/token")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer caller-token")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, nextExchange -> {
            forwarded.set(nextExchange);
            return Mono.empty();
        }).block();

        String expected = "Basic " + Base64.getEncoder().encodeToString(
                "web-client:secret%3Avalue".getBytes(StandardCharsets.UTF_8));
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo(expected);
    }

    @Test
    void shouldRejectUnknownClientRegistrationWithoutLeakingConfiguredSecret() {
        OAuth2ClientAuthenticationGatewayFilterFactory factory = factory(
                "management", "management", "server-secret");
        OAuth2ClientAuthenticationGatewayFilterFactory.Config config =
                new OAuth2ClientAuthenticationGatewayFilterFactory.Config();
        config.setRegistration("web");

        assertThatThrownBy(() -> factory.apply(config))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("chaos.gateway.oauth2-clients.web")
                .hasMessageNotContaining("server-secret");
    }

    private static OAuth2ClientAuthenticationGatewayFilterFactory factory(
            String registration,
            String clientId,
            String clientSecret) {
        ChaosGatewayProperties.OAuth2Client client = new ChaosGatewayProperties.OAuth2Client();
        client.setClientId(clientId);
        client.setClientSecret(clientSecret);
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.setOauth2Clients(Map.of(registration, client));
        return new OAuth2ClientAuthenticationGatewayFilterFactory(properties);
    }
}
