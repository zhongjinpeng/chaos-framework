package com.michael.chaos.web.xss;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 基础 XSS 防护过滤器。
 *
 * <p>通过包装请求对象对参数和请求头做最小 HTML 转义。复杂富文本场景应由业务侧采用专门清洗策略，
 * 或通过 {@code chaos.web.xss-exclude-paths} 配置排除路径。</p>
 */
public class XssFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final List<String> excludePaths;

    public XssFilter() {
        this(List.of());
    }

    public XssFilter(List<String> excludePaths) {
        this.excludePaths = excludePaths == null ? List.of() : List.copyOf(excludePaths);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isExcluded(request.getRequestURI())) {
            filterChain.doFilter(request, response);
        } else {
            filterChain.doFilter(new XssRequestWrapper(request), response);
        }
    }

    private boolean isExcluded(String uri) {
        for (String pattern : excludePaths) {
            if (PATH_MATCHER.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }
}
