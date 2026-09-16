package com.michael.chaos.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 可信代理客户端 IP 解析算法测试。Servlet 与 WebFlux 适配器共用这些规则。
 */
class ForwardedClientIpResolverTest {

    private final ForwardedClientIpResolver resolver = new ForwardedClientIpResolver(List.of("10.0.0.0/8"));

    /**
     * 未配置可信代理时，伪造的转发头必须被忽略。
     */
    @Test
    void shouldIgnoreForwardedHeadersWithoutTrustedProxies() {
        assertEquals("203.0.113.10", new ForwardedClientIpResolver().resolve("203.0.113.10", "1.1.1.1", "2.2.2.2"));
    }

    /**
     * 直连对端不是可信代理时同样忽略转发头。
     */
    @Test
    void shouldIgnoreForwardedHeadersFromUntrustedPeer() {
        assertEquals("203.0.113.10", resolver.resolve("203.0.113.10", "1.1.1.1", null));
    }

    /**
     * 来自可信代理时从右向左取第一个不可信地址，最左侧伪造的值不生效。
     */
    @Test
    void shouldPickRightMostUntrustedAddress() {
        assertEquals("198.51.100.7", resolver.resolve("10.0.0.5", "6.6.6.6, 198.51.100.7, 10.0.0.9", null));
    }

    /**
     * 最右侧出现非法值时回退到直连地址，主机名不会触发 DNS 解析。
     */
    @Test
    void shouldFallbackToRemoteWhenRightMostHopIsMalformed() {
        assertEquals("10.0.0.5", resolver.resolve("10.0.0.5", "198.51.100.7, evil-host", null));
        assertEquals("10.0.0.2", resolver.resolve("10.0.0.2", "attacker.example.com", null));
    }

    /**
     * 链路中间出现非法值时退回到它右侧最近的可信一跳。
     */
    @Test
    void shouldFallbackToNearestTrustedHopWhenChainIsMalformed() {
        assertEquals("10.0.0.8", resolver.resolve("10.0.0.5", "evil-host, 10.0.0.8", null));
    }

    /**
     * 链路全部是可信代理时使用最左侧地址。
     */
    @Test
    void shouldUseLeftMostWhenAllHopsAreTrusted() {
        assertEquals("10.1.1.1", resolver.resolve("10.0.0.5", "10.1.1.1, 10.0.0.9", null));
    }

    /**
     * 没有 X-Forwarded-For 时回退到合法的 X-Real-IP。
     */
    @Test
    void shouldFallbackToRealIp() {
        assertEquals("198.51.100.20", resolver.resolve("10.0.0.5", null, "198.51.100.20"));
        assertEquals("10.0.0.5", resolver.resolve("10.0.0.5", " ", "not-an-ip"));
    }

    /**
     * 可信代理判断对外暴露，用于决定是否信任身份透传头。
     */
    @Test
    void shouldExposeTrustedProxyCheck() {
        assertTrue(resolver.isTrustedProxy("10.2.3.4"));
        assertFalse(resolver.isTrustedProxy("203.0.113.10"));
        assertTrue(resolver.hasTrustedProxies());
        assertFalse(new ForwardedClientIpResolver().hasTrustedProxies());
    }

    /**
     * 宽松匹配忽略非法规则，合法规则仍然生效。
     */
    @Test
    void lenientMatchShouldSkipInvalidRules() {
        assertTrue(CidrMatcher.matchesAny("203.0.113.10", List.of("bad-rule", "203.0.113.0/24")));
        assertFalse(CidrMatcher.matchesAny("203.0.113.10", List.of("bad-rule")));
    }
}
