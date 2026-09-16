package com.michael.chaos.web.support;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.core.net.CidrMatcher;
import com.michael.chaos.core.net.ForwardedClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Objects;

/**
 * Servlet 客户端 IP 解析适配器。
 *
 * <p>解析规则统一由 chaos-core 的 {@link ForwardedClientIpResolver} 实现（可信代理 + {@code X-Forwarded-For} 从右向左），
 * 本类只负责从 {@link HttpServletRequest} 取出直连地址和转发头，保证网关与下游服务对同一请求解析出同一个 IP。</p>
 *
 * <ul>
 *     <li>未配置可信代理：始终返回 {@code remoteAddr}。如果应用前面有反向代理，
 *     可以使用 Spring Boot 的 {@code server.forward-headers-strategy=native}，由容器负责改写 remoteAddr。</li>
 *     <li>配置了可信代理：仅在 remoteAddr 命中白名单时解析转发头。</li>
 * </ul>
 */
public class ClientIpResolver {

    private static final String X_REAL_IP = "X-Real-IP";

    private final ForwardedClientIpResolver delegate;

    /**
     * 创建不信任任何代理的解析器。
     */
    public ClientIpResolver() {
        this(CidrMatcher.none());
    }

    /**
     * 使用可信代理 CIDR 列表创建解析器。
     *
     * @param trustedProxies 可信代理 IP 或 CIDR
     */
    public ClientIpResolver(Collection<String> trustedProxies) {
        this(CidrMatcher.of(trustedProxies));
    }

    /**
     * 使用可信代理匹配器创建解析器。
     *
     * @param trustedProxies 可信代理匹配器
     */
    public ClientIpResolver(CidrMatcher trustedProxies) {
        this.delegate = new ForwardedClientIpResolver(Objects.requireNonNull(trustedProxies, "trustedProxies must not be null"));
    }

    /**
     * 解析真实客户端 IP。
     *
     * @param request 当前请求
     * @return 客户端 IP，无法解析时返回 remoteAddr
     */
    public String resolve(HttpServletRequest request) {
        return delegate.resolve(
                request.getRemoteAddr(),
                request.getHeader(ChaosHeaders.X_FORWARDED_FOR),
                request.getHeader(X_REAL_IP));
    }

    /**
     * 判断当前请求的直连对端是否为可信代理。
     *
     * <p>该判断也用于决定是否信任上游透传的用户、租户身份请求头。</p>
     *
     * @param request 当前请求
     * @return 直连对端命中可信代理白名单时返回 {@code true}
     */
    public boolean isTrustedProxy(HttpServletRequest request) {
        return delegate.isTrustedProxy(request.getRemoteAddr());
    }

    /**
     * 是否配置了可信代理。
     */
    public boolean hasTrustedProxies() {
        return delegate.hasTrustedProxies();
    }
}
