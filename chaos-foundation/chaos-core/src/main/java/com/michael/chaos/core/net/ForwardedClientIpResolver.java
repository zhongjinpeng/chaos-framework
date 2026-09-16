package com.michael.chaos.core.net;

import java.util.Collection;
import java.util.Objects;

/**
 * 基于可信代理的客户端 IP 解析算法（Servlet 与 WebFlux 共用）。
 *
 * <p>安全模型：{@code X-Forwarded-For}/{@code X-Real-IP} 可以被任何客户端伪造，只有请求的直连对端是可信代理时才解析。
 * 此前 chaos-web 与 chaos-gateway 各有一份实现，细节不一致（非法值回退位置、是否支持 {@code X-Real-IP}），
 * 同一请求在网关和下游服务里可能解析出不同 IP，导致黑名单、限流和审计口径不一致。这里统一为一份与协议栈无关的算法，
 * Web 与网关只负责从各自的请求对象中取出直连地址和请求头。</p>
 *
 * <p>解析规则：</p>
 * <ol>
 *     <li>直连地址不是可信代理（或未配置可信代理）：直接使用直连地址，忽略全部转发头；</li>
 *     <li>存在 {@code X-Forwarded-For}：从右往左跳过可信代理，第一个不可信的地址即客户端 IP——最左侧的值可被伪造，不能直接使用；</li>
 *     <li>链路中出现非 IP 字面量：说明该位置及其左侧不可信，退回到它右侧最近的一跳（没有则为直连地址）；</li>
 *     <li>链路全部是可信代理：使用最左侧地址；</li>
 *     <li>没有 {@code X-Forwarded-For} 时回退到合法的 {@code X-Real-IP}，否则使用直连地址。</li>
 * </ol>
 *
 * <p>只接受字面量 IP，不做 DNS 解析；最多检查 {@value #MAX_FORWARDED_ENTRIES} 跳，防止超长请求头消耗 CPU。</p>
 */
public final class ForwardedClientIpResolver {

    /**
     * 最多检查的转发链路跳数。
     */
    public static final int MAX_FORWARDED_ENTRIES = 32;

    private final CidrMatcher trustedProxies;

    /**
     * 创建不信任任何代理的解析器。
     */
    public ForwardedClientIpResolver() {
        this(CidrMatcher.none());
    }

    /**
     * 使用可信代理 IP/CIDR 列表创建解析器。
     *
     * @param trustedProxies 可信代理 IP 或 CIDR
     * @throws IllegalArgumentException 存在无法解析的条目时抛出，保证配置错误在启动期暴露
     */
    public ForwardedClientIpResolver(Collection<String> trustedProxies) {
        this(CidrMatcher.of(trustedProxies));
    }

    /**
     * 使用可信代理匹配器创建解析器。
     */
    public ForwardedClientIpResolver(CidrMatcher trustedProxies) {
        this.trustedProxies = Objects.requireNonNull(trustedProxies, "trustedProxies must not be null");
    }

    /**
     * 解析真实客户端 IP。
     *
     * @param remoteAddress 直连对端地址，可为空
     * @param forwardedFor {@code X-Forwarded-For} 请求头，可为空
     * @param realIp {@code X-Real-IP} 请求头，可为空
     * @return 客户端 IP；直连地址为空且无法从转发头确定时返回空字符串
     */
    public String resolve(String remoteAddress, String forwardedFor, String realIp) {
        String remote = remoteAddress == null ? "" : remoteAddress.trim();
        if (!isTrustedProxy(remote)) {
            return remote;
        }
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return resolveForwardedChain(remote, forwardedFor.split(","));
        }
        if (CidrMatcher.isIpLiteral(realIp)) {
            return realIp.trim();
        }
        return remote;
    }

    /**
     * 判断直连对端是否为可信代理；该判断也用于决定是否信任上游透传的身份请求头。
     */
    public boolean isTrustedProxy(String remoteAddress) {
        return trustedProxies.matches(remoteAddress);
    }

    /**
     * 是否配置了可信代理。
     */
    public boolean hasTrustedProxies() {
        return !trustedProxies.isEmpty();
    }

    private String resolveForwardedChain(String remote, String[] entries) {
        int lowest = Math.max(0, entries.length - MAX_FORWARDED_ENTRIES);
        String nearestTrustedHop = remote;
        for (int i = entries.length - 1; i >= lowest; i--) {
            String hop = entries[i].trim();
            if (!CidrMatcher.isIpLiteral(hop)) {
                return nearestTrustedHop;
            }
            if (!trustedProxies.matches(hop)) {
                return hop;
            }
            nearestTrustedHop = hop;
        }
        return nearestTrustedHop;
    }
}
