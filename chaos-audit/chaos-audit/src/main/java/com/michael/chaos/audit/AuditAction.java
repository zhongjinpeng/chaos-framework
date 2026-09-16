package com.michael.chaos.audit;

/**
 * 框架内置审计动作编码。
 *
 * <p>动作编码保持稳定，方便 SIEM、日志平台和告警规则按精确值匹配。</p>
 */
public final class AuditAction {

    /**
     * 登录成功。
     */
    public static final String AUTH_LOGIN_SUCCESS = "auth.login.success";

    /**
     * 登录失败。
     */
    public static final String AUTH_LOGIN_FAILURE = "auth.login.failure";

    /**
     * Token 主动撤销。
     */
    public static final String AUTH_TOKEN_REVOKE = "auth.token.revoke";

    /**
     * Refresh token 刷新成功。
     */
    public static final String AUTH_REFRESH_SUCCESS = "auth.refresh.success";

    /**
     * Refresh token 重放嫌疑。
     */
    public static final String AUTH_REFRESH_REPLAY = "auth.refresh.replay";

    /**
     * 登录互踢。
     */
    public static final String AUTH_SESSION_KICKOUT = "auth.session.kickout";

    /**
     * 方法权限拒绝。
     */
    public static final String SECURITY_PERMISSION_DENIED = "security.permission.denied";

    /**
     * 网关认证失败。
     */
    public static final String GATEWAY_AUTH_DENIED = "gateway.auth.denied";

    /**
     * 网关黑名单拒绝。
     */
    public static final String GATEWAY_BLACKLIST_DENIED = "gateway.blacklist.denied";

    /**
     * 网关租户状态拒绝。
     */
    public static final String GATEWAY_TENANT_DENIED = "gateway.tenant.denied";

    private AuditAction() {
    }
}
