package com.michael.chaos.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 客户端 IP 解析测试。
 */
class ClientIpResolverTest {

    /**
     * 未配置可信代理时，伪造的 X-Forwarded-For 必须被忽略。
     */
    @Test
    void shouldIgnoreForwardedForWithoutTrustedProxies() {
        MockHttpServletRequest request = request("203.0.113.10", "1.1.1.1");

        assertThat(new ClientIpResolver().resolve(request)).isEqualTo("203.0.113.10");
    }

    /**
     * 直连对端不是可信代理时，同样忽略 X-Forwarded-For。
     */
    @Test
    void shouldIgnoreForwardedForFromUntrustedPeer() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));

        assertThat(resolver.resolve(request("203.0.113.10", "1.1.1.1"))).isEqualTo("203.0.113.10");
    }

    /**
     * 来自可信代理时从右向左取第一个不可信地址，客户端在最左侧伪造的值不会生效。
     */
    @Test
    void shouldPickRightMostUntrustedAddress() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));

        assertThat(resolver.resolve(request("10.0.0.5", "6.6.6.6, 198.51.100.7, 10.0.0.9")))
                .isEqualTo("198.51.100.7");
    }

    /**
     * 转发链路中出现非法值时回退到直连地址。
     */
    @Test
    void shouldFallbackWhenForwardedChainIsMalformed() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));

        assertThat(resolver.resolve(request("10.0.0.5", "198.51.100.7, evil-host"))).isEqualTo("10.0.0.5");
    }

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        request.setRemoteAddr(remoteAddr);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
