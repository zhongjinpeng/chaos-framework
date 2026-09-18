package com.michael.chaos.gateway.filter;

import com.michael.chaos.security.api.access.AccessSubject;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.web.server.ServerWebExchange;

/**
 * Gateway 过滤器之间共享的 exchange attribute。
 *
 * <p>身份信息只通过 attribute 在网关内部传递，下游请求头由网关在认证成功后统一写入，
 * 避免任何过滤器误读客户端伪造的身份请求头。</p>
 */
public final class GatewayExchangeAttributes {

    /**
     * 入站时被剔除的内部请求头原始值，key 为小写请求头名称。
     */
    public static final String STRIPPED_HEADERS = GatewayExchangeAttributes.class.getName() + ".strippedHeaders";

    /**
     * 认证成功后的用户 ID。
     */
    public static final String AUTHENTICATED_USER_ID = GatewayExchangeAttributes.class.getName() + ".userId";

    /**
     * 认证成功后 token 中携带的租户 ID。
     */
    public static final String AUTHENTICATED_TENANT_ID = GatewayExchangeAttributes.class.getName() + ".tenantId";

    /**
     * 认证成功后 token 中携带的角色集合。
     */
    public static final String AUTHENTICATED_ROLES = GatewayExchangeAttributes.class.getName() + ".roles";

    /**
     * 认证成功后 token 中携带的权限集合。
     */
    public static final String AUTHENTICATED_PERMISSIONS = GatewayExchangeAttributes.class.getName() + ".permissions";

    private GatewayExchangeAttributes() {
    }

    /**
     * 读取客户端原始请求头：优先读取被剔除前的值，其次读取当前请求头。
     *
     * <p>仅用于"客户端声明的意图"（例如希望访问的租户），绝不能直接当作已认证身份使用。</p>
     */
    public static String clientHeader(ServerWebExchange exchange, String headerName) {
        Map<String, String> stripped = exchange.getAttribute(STRIPPED_HEADERS);
        if (stripped != null) {
            String value = stripped.get(headerName.toLowerCase(Locale.ROOT));
            if (value != null) {
                return value;
            }
        }
        return exchange.getRequest().getHeaders().getFirst(headerName);
    }

    /**
     * 读取认证成功后的租户 ID；未认证或 token 不含租户时返回空字符串。
     */
    public static String authenticatedTenantId(ServerWebExchange exchange) {
        String value = exchange.getAttribute(AUTHENTICATED_TENANT_ID);
        return value == null ? "" : value;
    }

    /**
     * 读取认证成功后的用户 ID；未认证时返回空字符串。
     */
    public static String authenticatedUserId(ServerWebExchange exchange) {
        String value = exchange.getAttribute(AUTHENTICATED_USER_ID);
        return value == null ? "" : value;
    }

    /**
     * 读取认证成功后的角色集合；未认证时返回空集合。
     */
    public static Set<String> authenticatedRoles(ServerWebExchange exchange) {
        return stringSet(exchange, AUTHENTICATED_ROLES);
    }

    /**
     * 读取认证成功后的权限集合；未认证时返回空集合。
     */
    public static Set<String> authenticatedPermissions(ServerWebExchange exchange) {
        return stringSet(exchange, AUTHENTICATED_PERMISSIONS);
    }

    /**
     * 把认证结果转换为授权决策用的访问主体；未认证时返回匿名主体。
     *
     * <p>身份只取自 token 解析结果（attribute），绝不读请求头——请求头可以被客户端伪造。</p>
     */
    public static AccessSubject authenticatedSubject(ServerWebExchange exchange) {
        String userId = authenticatedUserId(exchange);
        if (userId.isBlank()) {
            return AccessSubject.ANONYMOUS;
        }
        return new AccessSubject(
                userId,
                userId,
                authenticatedTenantId(exchange),
                authenticatedRoles(exchange),
                authenticatedPermissions(exchange),
                Map.of());
    }

    /**
     * 写入认证结果中的角色与权限集合。
     */
    public static void putAuthenticatedAuthorities(
            ServerWebExchange exchange,
            Set<String> roles,
            Set<String> permissions) {
        if (roles != null && !roles.isEmpty()) {
            exchange.getAttributes().put(AUTHENTICATED_ROLES, Set.copyOf(roles));
        }
        if (permissions != null && !permissions.isEmpty()) {
            exchange.getAttributes().put(AUTHENTICATED_PERMISSIONS, Set.copyOf(permissions));
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> stringSet(ServerWebExchange exchange, String attributeName) {
        Object value = exchange.getAttribute(attributeName);
        return value instanceof Set<?> values ? (Set<String>) values : Set.of();
    }
}
