package com.michael.chaos.security.api.auth;

/**
 * Chaos JWT claim 名称。
 *
 * <p>授权服务器和资源服务器共享同一套 claim 常量，避免字符串散落导致 token 解析不一致。</p>
 */
public final class ChaosJwtClaims {

    /**
     * 用户 ID。
     */
    public static final String USER_ID = "userId";

    /**
     * 用户名。
     */
    public static final String USERNAME = "username";

    /**
     * 租户 ID。
     */
    public static final String TENANT_ID = "tenantId";

    /**
     * 角色编码集合。
     */
    public static final String ROLES = "roles";

    /**
     * 权限编码集合。
     */
    public static final String PERMISSIONS = "permissions";

    /**
     * OAuth2 客户端 ID。
     */
    public static final String CLIENT_ID = "clientId";

    /**
     * OAuth2 grant_type。
     */
    public static final String GRANT_TYPE = "grantType";

    /**
     * 登录设备 ID。
     */
    public static final String DEVICE_ID = "deviceId";

    /**
     * OpenID Connect 常用用户名 claim。
     */
    public static final String PREFERRED_USERNAME = "preferred_username";

    private ChaosJwtClaims() {
    }
}
