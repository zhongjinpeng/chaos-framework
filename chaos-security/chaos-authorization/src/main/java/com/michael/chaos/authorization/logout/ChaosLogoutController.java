package com.michael.chaos.authorization.logout;

import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.authorization.kickout.AuthorizationSessionRegistry;
import com.michael.chaos.authorization.kickout.AuthorizationTokenRevoker;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 框架统一退出登录端点。
 *
 * <p>退出时依次完成：把 access token 写入 JWT 撤销服务、清理互踢会话索引、移除授权记录、发布审计事件。
 * 仅删除授权记录对 JWT 自包含 token 无效——资源服务器本地验签不会查询授权记录，
 * 如果不写入撤销服务，注销后的 token 在剩余有效期内仍可访问资源。</p>
 *
 * <p>该端点幂等：无 token 或 token 不存在均返回 {@code {revoked: false}}。业务系统可通过
 * {@code chaos.authorization.logout} 调整路径或关闭后自行实现。</p>
 */
@RestController
public class ChaosLogoutController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final OAuth2AuthorizationService authorizationService;

    private final AuthorizationSessionRegistry sessionRegistry;

    private final AuthorizationTokenRevoker tokenRevoker;

    private final AuditEventPublisher auditEventPublisher;

    /**
     * 创建完整的退出端点。
     *
     * @param authorizationService 授权记录存储
     * @param sessionRegistry 互踢会话索引，可为空
     * @param tokenRevoker access token 撤销器，可为空
     * @param auditEventPublisher 审计事件发布器
     */
    public ChaosLogoutController(
            OAuth2AuthorizationService authorizationService,
            AuthorizationSessionRegistry sessionRegistry,
            AuthorizationTokenRevoker tokenRevoker,
            AuditEventPublisher auditEventPublisher) {
        this.authorizationService = authorizationService;
        this.sessionRegistry = sessionRegistry;
        this.tokenRevoker = tokenRevoker;
        this.auditEventPublisher = auditEventPublisher == null ? new NoopAuditEventPublisher() : auditEventPublisher;
    }

    /**
     * 退出登录：撤销当前 access token，使其立即失效。
     *
     * <p>路径由 {@code chaos.authorization.logout.path} 配置，默认 {@code /api/v1/auth/logout}。</p>
     */
    @PostMapping("${chaos.authorization.logout.path:/api/v1/auth/logout}")
    public Map<String, Object> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        boolean revoked = revoke(extractBearer(authorization));
        return Map.of("revoked", revoked);
    }

    private boolean revoke(String bearerToken) {
        if (!StringUtils.hasText(bearerToken)) {
            return false;
        }
        OAuth2Authorization authorization =
                authorizationService.findByToken(bearerToken, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null) {
            return false;
        }
        if (tokenRevoker != null) {
            tokenRevoker.revokeAccessToken(authorization);
        }
        if (sessionRegistry != null) {
            sessionRegistry.remove(authorization);
        }
        authorizationService.remove(authorization);
        auditEventPublisher.publish(AuditSupport.event(AuditAction.AUTH_TOKEN_REVOKE, AuditOutcome.SUCCESS)
                .principalId(authorization.getPrincipalName())
                .clientId(authorization.getRegisteredClientId())
                .reason("logout")
                .attributes(AuditSupport.attributes("authorizationId", authorization.getId()))
                .build());
        return true;
    }

    private String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }
}
