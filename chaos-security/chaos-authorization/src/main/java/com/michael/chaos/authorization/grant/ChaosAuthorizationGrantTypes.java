package com.michael.chaos.authorization.grant;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * chaos 授权模块内置 grant_type 常量。
 */
public final class ChaosAuthorizationGrantTypes {

    /**
     * 用户名密码登录 grant_type。
     */
    public static final AuthorizationGrantType PASSWORD = new AuthorizationGrantType("password");

    /**
     * 手机号验证码登录 grant_type。
     */
    public static final AuthorizationGrantType SMS_CODE = new AuthorizationGrantType("sms_code");

    private ChaosAuthorizationGrantTypes() {
    }
}
