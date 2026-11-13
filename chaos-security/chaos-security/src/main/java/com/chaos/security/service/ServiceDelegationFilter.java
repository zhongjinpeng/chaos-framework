package com.chaos.security.service;

import com.chaos.security.api.auth.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 服务间委托身份过滤器。
 *
 * <p>只接受已通过 OAuth2 introspection 的服务令牌：按 {@code client_id} 找到调用方，
 * 校验受信调用方传来的租户与操作人，转换为 {@link LoginUser} 后继续过滤链，结束时恢复原认证。
 * 服务令牌、调用方清单、委托头名和请求体上限全部由构造参数决定，不绑定具体产品。</p>
 */
public class ServiceDelegationFilter extends OncePerRequestFilter {

    private final String requiredScope;

    private final Map<String, Caller> callers;

    private final String tenantHeader;

    private final String operatorHeader;

    private final long maxBodyBytes;

    /**
     * 创建服务委托身份过滤器。
     *
     * @param requiredScope 服务令牌必须包含的 scope
     * @param callers 允许的服务客户端标识到调用方规则的映射
     * @param tenantHeader 受信调用方传递资源归属租户的请求头
     * @param operatorHeader 受信调用方传递操作人身份的请求头
     * @param maxBodyBytes 允许的最大请求体字节数，非正数表示不限制
     */
    public ServiceDelegationFilter(
            String requiredScope,
            Map<String, Caller> callers,
            String tenantHeader,
            String operatorHeader,
            long maxBodyBytes) {
        this.requiredScope = requiredScope;
        this.callers = Map.copyOf(callers);
        this.tenantHeader = tenantHeader;
        this.operatorHeader = operatorHeader;
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof BearerTokenAuthentication bearer)
                || !bearer.getToken().getScopes().contains(requiredScope)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String client = String.valueOf(bearer.getTokenAttributes().get("client_id"));
        Caller caller = callers.get(client);
        String tenant = request.getHeader(tenantHeader);
        String operator = request.getHeader(operatorHeader);
        if (caller == null || !identity(tenant) || !identity(operator) || !caller.allows(request.getRequestURI())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (maxBodyBytes > 0 && request.getContentLengthLong() > maxBodyBytes) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        var delegated = new UsernamePasswordAuthenticationToken(
                new LoginUser(operator, operator, tenant, Set.of(), Set.of()),
                null,
                bearer.getToken().getScopes().stream()
                        .map(granted -> new SimpleGrantedAuthority("SCOPE_" + granted))
                        .toList());
        delegated.setDetails(caller.name());
        SecurityContextHolder.getContext().setAuthentication(delegated);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    private static boolean identity(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= 64
                && value.equals(value.strip())
                && value.chars().noneMatch(Character::isISOControl);
    }

    /**
     * 允许的服务调用方及其可访问的路径前缀。
     *
     * @param name 记录到认证 details 的调用方名称
     * @param pathPrefixes 允许访问的路径前缀；为空表示不限制路径
     */
    public record Caller(String name, List<String> pathPrefixes) {

        /**
         * 校验并复制调用方规则。
         */
        public Caller {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("调用方名称不能为空");
            }
            pathPrefixes = pathPrefixes == null ? List.of() : List.copyOf(pathPrefixes);
        }

        boolean allows(String path) {
            return pathPrefixes.isEmpty() || pathPrefixes.stream().anyMatch(path::startsWith);
        }
    }
}
