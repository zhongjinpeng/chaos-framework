package com.michael.chaos.web.ratelimit;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.web.support.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.web.method.HandlerMethod;

/**
 * 默认限流 key 解析器。
 *
 * <p>维度：方法签名 + 租户 + 用户（已认证）或客户端 IP（匿名）。</p>
 *
 * <p>客户端 IP 通过 {@link ClientIpResolver} 基于可信代理解析。原实现直接取 {@code X-Forwarded-For}
 * 第一个值，攻击者每次请求更换伪造 IP 即可完全绕过限流，并快速制造海量 key 撑爆本地限流器。
 * 用户和租户来自 {@link RequestContext}，其身份请求头默认不被信任，不能再被伪造。</p>
 */
public class DefaultRateLimitKeyResolver implements RateLimitKeyResolver {

    private final ClientIpResolver clientIpResolver;

    /**
     * 创建不信任任何代理的解析器，客户端 IP 取直连地址。
     */
    public DefaultRateLimitKeyResolver() {
        this(new ClientIpResolver());
    }

    /**
     * 使用指定客户端 IP 解析器创建限流 key 解析器。
     *
     * @param clientIpResolver 客户端 IP 解析器
     */
    public DefaultRateLimitKeyResolver(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver must not be null");
    }

    @Override
    public String resolve(HttpServletRequest request, HandlerMethod handlerMethod) {
        String userId = RequestContext.userId();
        String principal = userId.isBlank() ? "ip:" + clientIpResolver.resolve(request) : "user:" + userId;
        return handlerMethod.getBeanType().getName()
                + "#"
                + handlerMethod.getMethod().getName()
                + ":tenant="
                + RequestContext.tenantId()
                + ":principal="
                + principal;
    }
}
