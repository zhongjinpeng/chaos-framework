package com.michael.chaos.authorization.core;

import com.michael.chaos.security.api.auth.LoginUser;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * 默认拒绝所有登录的用户服务。
 *
 * <p>业务系统启用授权服务器时必须提供自己的 {@link ChaosAuthorizationUserService} Bean。</p>
 */
public class RejectingChaosAuthorizationUserService implements ChaosAuthorizationUserService {

    /**
     * 默认拒绝用户名密码登录。
     */
    @Override
    public LoginUser authenticateByUsername(String username, String password) {
        throw unsupported();
    }

    /**
     * 默认拒绝手机号验证码登录。
     */
    @Override
    public LoginUser loadByMobile(String mobile) {
        throw unsupported();
    }

    /**
     * 创建缺少业务用户服务的 OAuth2 异常。
     */
    private OAuth2AuthenticationException unsupported() {
        return new OAuth2AuthenticationException(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_GRANT,
                "ChaosAuthorizationUserService bean is required",
                null
        ));
    }
}
