package com.michael.chaos.gateway.filter;

import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import java.util.List;
import java.util.Locale;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

/**
 * Gateway 路径匹配工具，统一白名单、限流跳过路径等场景的匹配规则。
 *
 * <p>所有过滤器都必须通过本类匹配路径，而不是各自创建 {@link AntPathMatcher}：
 * 白名单按解码后的路径匹配，下游容器还会再做一次规范化，如果请求包含 {@code ..}、编码斜杠等歧义写法，
 * {@code /api/public/../orders} 会命中 {@code /api/public/**} 白名单，但实际访问的是 {@code /api/orders}。
 * 因此歧义路径永远不视为命中任何放行规则（fail closed）。</p>
 */
public final class GatewayWhitelistMatcher {

    private static final String ATTR_KEY = GatewayWhitelistMatcher.class.getName() + ".whitelisted";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> AMBIGUOUS_RAW_TOKENS = List.of("%2e", "%2f", "%5c", "%25", "%00", ";", "\\");

    private GatewayWhitelistMatcher() {
    }

    /**
     * 判断请求是否命中鉴权白名单，结果缓存到 exchange attribute 避免重复匹配。
     */
    public static boolean isWhitelisted(ServerWebExchange exchange, ChaosGatewayProperties properties) {
        Boolean cached = exchange.getAttribute(ATTR_KEY);
        if (cached != null) {
            return cached;
        }
        boolean result = matchesAny(exchange, properties.getWhitelist());
        exchange.getAttributes().put(ATTR_KEY, result);
        return result;
    }

    /**
     * 判断请求路径是否命中任意 Ant 风格表达式；歧义路径一律返回 {@code false}。
     */
    public static boolean matchesAny(ServerWebExchange exchange, List<String> patterns) {
        ServerHttpRequest request = exchange.getRequest();
        if (patterns == null || patterns.isEmpty() || isAmbiguous(request)) {
            return false;
        }
        String path = request.getURI().getPath();
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断单个路径是否命中 Ant 风格表达式。
     */
    public static boolean matches(String pattern, String path) {
        return PATH_MATCHER.match(pattern, path);
    }

    /**
     * 判断请求路径是否包含路径穿越或歧义编码。
     */
    public static boolean isAmbiguous(ServerHttpRequest request) {
        String rawPath = request.getURI().getRawPath();
        if (rawPath == null) {
            return false;
        }
        String lower = rawPath.toLowerCase(Locale.ROOT);
        for (String token : AMBIGUOUS_RAW_TOKENS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        String decodedPath = request.getURI().getPath();
        if (decodedPath == null) {
            return false;
        }
        for (String segment : decodedPath.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                return true;
            }
        }
        return false;
    }
}
