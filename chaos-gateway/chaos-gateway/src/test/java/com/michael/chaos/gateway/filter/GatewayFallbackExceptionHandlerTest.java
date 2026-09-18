package com.michael.chaos.gateway.filter;

import static com.michael.chaos.gateway.filter.GatewayFilterTestSupport.responseBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

/**
 * Gateway 统一降级异常处理器测试。
 */
class GatewayFallbackExceptionHandlerTest {

    /**
     * 下游连接失败时应返回 503 JSON。
     */
    @Test
    void shouldWriteServiceUnavailableWhenBackendConnectFailed() {
        GatewayFallbackExceptionHandler handler = new GatewayFallbackExceptionHandler(new ChaosGatewayProperties());
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(handler.handle(exchange, new ConnectException("connection refused"))).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(responseBody(exchange)).contains("\"code\":\"503\"");
        assertThat(responseBody(exchange)).contains("\"traceId\"");
    }

    /**
     * 未知异常开启统一处理时应返回 500 JSON。
     */
    @Test
    void shouldWriteInternalErrorWhenUnhandledIncluded() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getFallback().setIncludeUnhandled(true);
        GatewayFallbackExceptionHandler handler = new GatewayFallbackExceptionHandler(properties);
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(handler.handle(exchange, new IllegalStateException("boom"))).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(responseBody(exchange)).contains("\"code\":\"500\"");
    }

    /**
     * 关闭降级处理时应继续抛出原异常。
     */
    @Test
    void shouldPropagateWhenFallbackDisabled() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getFallback().setEnabled(false);
        GatewayFallbackExceptionHandler handler = new GatewayFallbackExceptionHandler(properties);
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(handler.handle(exchange, new ConnectException("connection refused")))
                .expectError(ConnectException.class)
                .verify();
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));
    }

}
