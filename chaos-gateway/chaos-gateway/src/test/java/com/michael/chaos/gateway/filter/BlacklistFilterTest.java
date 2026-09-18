package com.michael.chaos.gateway.filter;

import static com.michael.chaos.gateway.filter.GatewayFilterTestSupport.responseBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.test.audit.CapturingAuditEventPublisher;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Gateway IP 黑名单过滤器测试。
 */
class BlacklistFilterTest {

    /**
     * 直连地址是可信代理时，应从右往左跳过可信代理，取第一个不可信地址命中精确黑名单。
     */
    @Test
    void shouldMatchForwardedExactIpFromTrustedProxy() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.setBlacklist(List.of("10.0.0.10"));
        properties.setTrustedProxies(List.of("192.168.1.0/24"));
        BlacklistFilter filter = new BlacklistFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .remoteAddress(address("192.168.1.2"))
                .header("X-Forwarded-For", "10.0.0.10, 192.168.1.1"));

        StepVerifier.create(filter.filter(exchange, next -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(responseBody(exchange)).contains("\"code\":\"403\"");
    }

    /**
     * 应支持 CIDR 黑名单。
     */
    @Test
    void shouldMatchCidrRule() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.setBlacklist(List.of("10.0.0.0/24"));
        properties.setTrustedProxies(List.of("127.0.0.1"));
        CapturingAuditEventPublisher publisher = new CapturingAuditEventPublisher();
        BlacklistFilter filter = new BlacklistFilter(properties, publisher);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .remoteAddress(address("127.0.0.1"))
                .header("X-Real-IP", "10.0.0.88"));

        StepVerifier.create(filter.filter(exchange, next -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(responseBody(exchange)).contains("\"message\":\"forbidden\"");
        assertThat(publisher.events()).hasSize(1);
        assertThat(publisher.events().getFirst().action()).isEqualTo("gateway.blacklist.denied");
        assertThat(publisher.events().getFirst().attributes()).containsEntry("rule", "10.0.0.0/24");
    }

    /**
     * 未命中黑名单时继续执行后续过滤器。
     */
    @Test
    void shouldContinueWhenNotMatched() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.setBlacklist(List.of("10.0.0.0/24"));
        BlacklistFilter filter = new BlacklistFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header("X-Real-IP", "10.0.1.88"));

        StepVerifier.create(filter.filter(exchange, next -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * 未配置可信代理时，被拉黑的直连地址伪造 X-Forwarded-For 也必须被拦截。
     */
    @Test
    void shouldIgnoreSpoofedForwardedForFromUntrustedClient() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.setBlacklist(List.of("203.0.113.10"));
        BlacklistFilter filter = new BlacklistFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .remoteAddress(address("203.0.113.10"))
                .header("X-Forwarded-For", "1.1.1.1")
                .header("X-Real-IP", "1.1.1.1"));

        StepVerifier.create(filter.filter(exchange, next -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * 不可信客户端不能通过伪造 X-Forwarded-For 冒充黑名单外的 IP，也不能把自己伪装成黑名单 IP 的受害者。
     */
    @Test
    void shouldNotBlockBasedOnSpoofedHeaderFromUntrustedClient() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.setBlacklist(List.of("10.0.0.10"));
        BlacklistFilter filter = new BlacklistFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .remoteAddress(address("198.51.100.7"))
                .header("X-Forwarded-For", "10.0.0.10"));

        StepVerifier.create(filter.filter(exchange, next -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private static InetSocketAddress address(String ip) {
        return new InetSocketAddress(ip, 40000);
    }

}
