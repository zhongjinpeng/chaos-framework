package com.michael.chaos.gateway.ratelimit;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.gateway.filter.ClientIpResolver;
import com.michael.chaos.gateway.filter.GatewayExchangeAttributes;
import java.util.List;
import java.util.Locale;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * Gateway 限流 key 解析器。
 */
public class GatewayRateLimitKeyResolver {

    private final List<String> trustedProxies;

    /**
     * 创建不信任任何代理转发头的 key 解析器。
     */
    public GatewayRateLimitKeyResolver() {
        this(List.of());
    }

    /**
     * 创建 key 解析器。
     *
     * @param trustedProxies 可信代理列表，只有直连地址命中时才解析转发头
     */
    public GatewayRateLimitKeyResolver(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? List.of() : trustedProxies;
    }

    /**
     * 根据请求上下文和限流维度生成稳定 key。
     */
    public String resolve(ServerWebExchange exchange, String ruleId, List<ChaosGatewayProperties.KeyType> keyTypes) {
        StringBuilder builder = new StringBuilder("gateway:").append(normalize(ruleId, "default"));
        for (ChaosGatewayProperties.KeyType keyType : keyTypes) {
            builder.append(':')
                    .append(keyType.name().toLowerCase(Locale.ROOT))
                    .append('=')
                    .append(value(exchange, keyType));
        }
        return builder.toString();
    }

    private String value(ServerWebExchange exchange, ChaosGatewayProperties.KeyType keyType) {
        return switch (keyType) {
            case ROUTE -> normalize(routeId(exchange), "unknown");
            case PATH -> normalize(exchange.getRequest().getURI().getPath(), "/");
            case IP -> normalize(ClientIpResolver.resolve(exchange, trustedProxies), "unknown");
            case USER -> normalize(firstNonBlank(
                    GatewayExchangeAttributes.authenticatedUserId(exchange),
                    exchange.getRequest().getHeaders().getFirst(ChaosHeaders.USER_ID)), "anonymous");
            case TENANT -> normalize(firstNonBlank(
                    GatewayExchangeAttributes.authenticatedTenantId(exchange),
                    exchange.getRequest().getHeaders().getFirst(ChaosHeaders.TENANT_ID)), "default");
        };
    }

    /**
     * 优先使用认证结果；请求头只可能来自网关内部写入（入站同名头已被剔除）。
     */
    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private String routeId(ServerWebExchange exchange) {
        Object routeId = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ATTR);
        if (routeId instanceof String value && !value.isBlank()) {
            return value;
        }
        Object route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null) {
            return route.toString();
        }
        return "";
    }

    private String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
