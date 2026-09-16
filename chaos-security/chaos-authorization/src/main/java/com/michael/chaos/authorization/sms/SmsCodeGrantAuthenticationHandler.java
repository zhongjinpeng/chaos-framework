package com.michael.chaos.authorization.sms;

import com.michael.chaos.authorization.core.ChaosAuthorizationUserService;
import com.michael.chaos.authorization.grant.ChaosAuthorizationGrantTypes;
import com.michael.chaos.authorization.grant.ChaosGrantAuthenticationHandler;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Map;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * 默认手机号验证码 grant_type 处理器。
 */
public class SmsCodeGrantAuthenticationHandler implements ChaosGrantAuthenticationHandler {

    private static final String MOBILE = "mobile";

    private static final String CODE = "code";

    private final ChaosAuthorizationUserService userService;

    private final SmsCodeVerifier smsCodeVerifier;

    /**
     * 创建手机号验证码登录处理器。
     */
    public SmsCodeGrantAuthenticationHandler(
            ChaosAuthorizationUserService userService,
            SmsCodeVerifier smsCodeVerifier) {
        this.userService = userService;
        this.smsCodeVerifier = smsCodeVerifier;
    }

    /**
     * 返回 sms_code grant_type。
     */
    @Override
    public AuthorizationGrantType grantType() {
        return ChaosAuthorizationGrantTypes.SMS_CODE;
    }

    /**
     * 校验手机号、验证码参数，并在验证码通过后加载用户。
     */
    @Override
    public LoginUser authenticate(Map<String, Object> parameters) {
        String mobile = required(parameters, MOBILE);
        String code = required(parameters, CODE);
        if (!smsCodeVerifier.verify(mobile, code)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_GRANT,
                    "invalid sms code",
                    null
            ));
        }
        return userService.loadByMobile(mobile);
    }

    /**
     * 读取必填字符串参数。
     */
    private String required(Map<String, Object> parameters, String name) {
        Object value = parameters.get(name);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new OAuth2AuthenticationException(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_REQUEST,
                "parameter is required: " + name,
                null
        ));
    }
}
