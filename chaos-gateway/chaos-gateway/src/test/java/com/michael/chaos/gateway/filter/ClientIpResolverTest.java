package com.michael.chaos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * 客户端 IP 解析测试。
 */
class ClientIpResolverTest {

    /**
     * 未配置可信代理时必须忽略所有转发头。
     */
    @Test
    void shouldIgnoreForwardedHeadersWithoutTrustedProxies() {
        MockServerWebExchange exchange = exchange("203.0.113.10", "1.1.1.1");

        assertThat(ClientIpResolver.resolve(exchange, List.of())).isEqualTo("203.0.113.10");
    }

    /**
     * 直连地址不是可信代理时必须忽略转发头。
     */
    @Test
    void shouldIgnoreForwardedHeadersFromUntrustedRemote() {
        MockServerWebExchange exchange = exchange("203.0.113.10", "1.1.1.1");

        assertThat(ClientIpResolver.resolve(exchange, List.of("10.0.0.0/8"))).isEqualTo("203.0.113.10");
    }

    /**
     * 可信代理链路应从右往左取第一个不可信地址，而不是可被伪造的最左侧地址。
     */
    @Test
    void shouldPickRightmostUntrustedHop() {
        MockServerWebExchange exchange = exchange("10.0.0.2", "6.6.6.6, 198.51.100.20, 10.0.0.1");

        assertThat(ClientIpResolver.resolve(exchange, List.of("10.0.0.0/8"))).isEqualTo("198.51.100.20");
    }

    /**
     * 转发头中的主机名等非法值不能被当作客户端 IP，也不能触发 DNS 解析。
     */
    @Test
    void shouldFallbackWhenForwardedValueIsNotIpLiteral() {
        MockServerWebExchange exchange = exchange("10.0.0.2", "attacker.example.com");

        assertThat(ClientIpResolver.resolve(exchange, List.of("10.0.0.0/8"))).isEqualTo("10.0.0.2");
    }

    /**
     * 解析结果应缓存到 exchange，单参数重载复用该结果。
     */
    @Test
    void shouldCacheResolvedIp() {
        MockServerWebExchange exchange = exchange("10.0.0.2", "198.51.100.20");

        ClientIpResolver.resolve(exchange, List.of("10.0.0.2"));

        assertThat(ClientIpResolver.resolve(exchange)).isEqualTo("198.51.100.20");
    }

    private MockServerWebExchange exchange(String remote, String forwardedFor) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .remoteAddress(new InetSocketAddress(remote, 40000))
                .header("X-Forwarded-For", forwardedFor));
    }
}
