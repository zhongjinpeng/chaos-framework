package com.michael.chaos.security.token;

import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.api.token.JwtTokenIds;
import com.michael.chaos.security.config.ChaosSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 撤销检查过滤器。
 *
 * <p>被撤销的 token 返回 401，并输出与 {@code Result} 结构一致的 JSON 错误体；
 * 撤销服务自身异常时按 {@code chaos.security.jwt.revocation-fail-open} 显式决定放行还是返回 503，
 * 避免异常直接抛出后由容器输出不一致的错误页。</p>
 */
public class JwtRevocationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtRevocationFilter.class);

    private final JwtRevocationService revocationService;

    private final ChaosSecurityProperties properties;

    /**
     * 创建 JWT 撤销检查过滤器。
     */
    public JwtRevocationFilter(JwtRevocationService revocationService, ChaosSecurityProperties properties) {
        this.revocationService = revocationService;
        this.properties = properties;
    }

    /**
     * JWT 被撤销时直接返回 401。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!properties.getJwt().isRevocationCheckEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            boolean revoked;
            try {
                revoked = revocationService.isRevoked(
                        JwtTokenIds.resolve(token.getToken().getId(), token.getToken().getTokenValue()));
            } catch (RuntimeException ex) {
                if (properties.getJwt().isRevocationFailOpen()) {
                    LOGGER.warn("JWT revocation check failed, fail-open enabled: {}", ex.toString());
                    filterChain.doFilter(request, response);
                    return;
                }
                LOGGER.error("JWT revocation check failed, rejecting request (fail-closed)", ex);
                writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "503", "service unavailable");
                return;
            }
            if (revoked) {
                response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"token revoked\"");
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "401", "unauthorized");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"%s\",\"message\":\"%s\",\"data\":null,\"timestamp\":%d}"
                .formatted(code, message, Instant.now().toEpochMilli()));
    }
}
