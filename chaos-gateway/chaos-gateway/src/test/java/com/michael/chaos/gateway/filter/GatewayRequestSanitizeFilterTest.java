package com.michael.chaos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 入站请求净化过滤器测试。
 */
class GatewayRequestSanitizeFilterTest {

    /**
     * 客户端伪造的内部身份请求头必须被剔除，原始值只保存在 exchange attribute 中。
     */
    @Test
    void shouldStripSpoofedIdentityHeaders() {
        GatewayRequestSanitizeFilter filter = new GatewayRequestSanitizeFilter(new ChaosGatewayProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/ping")
                .header(ChaosHeaders.USER_ID, "1")
                .header(ChaosHeaders.TENANT_ID, "tenant-b"));

        StepVerifier.create(filter.filter(exchange, next -> {
            assertThat(next.getRequest().getHeaders().containsKey(ChaosHeaders.USER_ID)).isFalse();
            assertThat(next.getRequest().getHeaders().containsKey(ChaosHeaders.TENANT_ID)).isFalse();
            assertThat(GatewayExchangeAttributes.clientHeader(next, ChaosHeaders.TENANT_ID)).isEqualTo("tenant-b");
            return Mono.empty();
        })).verifyComplete();
    }

    /**
     * 剔除名单应支持自定义。
     */
    @Test
    void shouldStripConfiguredHeaders() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.setInternalHeaders(List.of("X-Internal-Role"));
        GatewayRequestSanitizeFilter filter = new GatewayRequestSanitizeFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header("x-internal-role", "admin"));

        StepVerifier.create(filter.filter(exchange, next -> {
            assertThat(next.getRequest().getHeaders().containsKey("X-Internal-Role")).isFalse();
            return Mono.empty();
        })).verifyComplete();
    }

    /**
     * 路径穿越和编码斜杠必须直接返回 400，不能进入后续过滤器。
     */
    @Test
    void shouldRejectAmbiguousPaths() {
        GatewayRequestSanitizeFilter filter = new GatewayRequestSanitizeFilter(new ChaosGatewayProperties());
        for (String path : List.of("/api/public/../orders", "/api/public/%2e%2e/orders",
                "/api/public%2Forders", "/api/public;jsessionid=1/orders")) {
            AtomicBoolean invoked = new AtomicBoolean();
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));

            StepVerifier.create(filter.filter(exchange, next -> {
                invoked.set(true);
                return Mono.empty();
            })).verifyComplete();

            assertThat(invoked).as(path).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).as(path).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 关闭歧义路径拒绝后应继续执行，但白名单匹配仍然 fail closed。
     */
    @Test
    void shouldNotWhitelistAmbiguousPathEvenWhenRejectDisabled() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.setRejectAmbiguousPath(false);
        properties.setWhitelist(List.of("/api/public/**"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/public/../orders"));

        assertThat(GatewayWhitelistMatcher.isWhitelisted(exchange, properties)).isFalse();
    }
}
