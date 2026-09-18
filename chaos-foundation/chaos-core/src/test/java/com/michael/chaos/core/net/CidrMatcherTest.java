package com.michael.chaos.core.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CIDR 匹配器测试。
 */
class CidrMatcherTest {

    /**
     * 可信代理白名单按 CIDR 配置，网段匹配错了等于把伪造的 X-Forwarded-For 当真。
     */
    @Test
    void shouldMatchIpv4Cidr() {
        CidrMatcher matcher = CidrMatcher.of(List.of("10.0.0.0/8", "192.168.1.10"));

        assertTrue(matcher.matches("10.1.2.3"));
        assertTrue(matcher.matches("192.168.1.10"));
        assertFalse(matcher.matches("192.168.1.11"));
        assertFalse(matcher.matches("11.0.0.1"));
    }

    /**
     * 容器环境里客户端地址常以 IPv6 映射形式出现，不能因此漏判可信代理。
     */
    @Test
    void shouldMatchIpv6AndMappedIpv4() {
        CidrMatcher matcher = CidrMatcher.of(List.of("fd00::/8", "127.0.0.1"));

        assertTrue(matcher.matches("fd12:3456::1"));
        assertTrue(matcher.matches("::ffff:127.0.0.1"));
        assertFalse(matcher.matches("2001:db8::1"));
    }

    /**
     * 非 8 位对齐的前缀（如 /26）必须按位比较，按字节比会把邻近网段一起放行。
     */
    @Test
    void shouldSupportNonByteAlignedPrefix() {
        CidrMatcher matcher = CidrMatcher.of(List.of("172.16.0.0/12"));

        assertTrue(matcher.matches("172.31.255.255"));
        assertFalse(matcher.matches("172.32.0.1"));
    }

    /**
     * 主机名和非法字面量不能被当作 IP，避免 DNS 解析和伪造值命中白名单。
     */
    @Test
    void shouldRejectHostnamesAndGarbage() {
        CidrMatcher matcher = CidrMatcher.of(List.of("0.0.0.0/0"));

        assertFalse(matcher.matches("localhost"));
        assertFalse(matcher.matches("999.1.1.1"));
        assertFalse(matcher.matches(""));
        assertFalse(matcher.matches(null));
        assertTrue(matcher.matches("8.8.8.8"));
    }

    /**
     * 非法网段写法要在启动期报错：运行期静默忽略等于白名单默默失效。
     */
    @Test
    void shouldFailFastOnIllegalConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> CidrMatcher.of(List.of("example.com")));
        assertThrows(IllegalArgumentException.class, () -> CidrMatcher.of(List.of("10.0.0.0/33")));
    }
}
