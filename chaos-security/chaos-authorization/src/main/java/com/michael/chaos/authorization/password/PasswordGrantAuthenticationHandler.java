package com.michael.chaos.authorization.password;

import com.michael.chaos.authorization.captcha.CaptchaService;
import com.michael.chaos.authorization.core.ChaosAuthorizationUserService;
import com.michael.chaos.authorization.grant.ChaosAuthorizationGrantTypes;
import com.michael.chaos.authorization.grant.ChaosGrantAuthenticationHandler;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * 默认用户名密码 grant_type 处理器。
 */
public class PasswordGrantAuthenticationHandler implements ChaosGrantAuthenticationHandler {

    private static final String USERNAME = "username";

    private static final String PASSWORD = "password";

    private static final String CAPTCHA_ID = "captcha_id";

    private static final String CAPTCHA_CODE = "captcha_code";

    private static final String TENANT_ID = "tenant_id";

    private static final String TENANT_ID_CAMEL = "tenantId";

    private static final String TENANT_CODE = "tenant_code";

    private static final String TENANT_CODE_CAMEL = "tenantCode";

    private final ChaosAuthorizationUserService userService;

    private final CaptchaService captchaService;

    private final LoginFailureLimiter loginFailureLimiter;

    /**
     * 创建用户名密码登录处理器。
     */
    public PasswordGrantAuthenticationHandler(ChaosAuthorizationUserService userService) {
        this(userService, null);
    }

    /**
     * 创建可选图形验证码校验的用户名密码登录处理器。
     */
    public PasswordGrantAuthenticationHandler(
            ChaosAuthorizationUserService userService,
            CaptchaService captchaService) {
        this(userService, captchaService, null);
    }

    /**
     * 创建带图形验证码和登录失败锁定的用户名密码登录处理器。
     *
     * @param userService 业务身份服务
     * @param captchaService 图形验证码服务，可为空
     * @param loginFailureLimiter 登录失败限制器，可为空（不锁定）
     */
    public PasswordGrantAuthenticationHandler(
            ChaosAuthorizationUserService userService,
            CaptchaService captchaService,
            LoginFailureLimiter loginFailureLimiter) {
        this.userService = userService;
        this.captchaService = captchaService;
        this.loginFailureLimiter = loginFailureLimiter;
    }

    /**
     * 返回 password grant_type。
     */
    @Override
    public AuthorizationGrantType grantType() {
        return ChaosAuthorizationGrantTypes.PASSWORD;
    }

    /**
     * 校验用户名和密码参数，并委托业务身份服务认证。
     */
    @Override
    public LoginUser authenticate(Map<String, Object> parameters) {
        String username = required(parameters, USERNAME);
        String password = required(parameters, PASSWORD);
        verifyCaptcha(parameters);
        String tenant = optionalTenant(parameters);
        String lockKey = lockKey(tenant, username);
        // 锁定检查放在密码校验之前：锁定期内不再调用业务身份服务，攻击者无法继续通过响应差异试探密码。
        if (loginFailureLimiter != null && loginFailureLimiter.isLocked(lockKey)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_GRANT,
                    "too many failed login attempts, please try again later",
                    null
            ));
        }
        LoginUser user;
        try {
            user = userService.authenticateByUsername(tenant, username, password);
        } catch (RuntimeException ex) {
            if (loginFailureLimiter != null) {
                loginFailureLimiter.recordFailure(lockKey);
            }
            throw ex;
        }
        if (loginFailureLimiter != null) {
            loginFailureLimiter.reset(lockKey);
        }
        return user;
    }

    /**
     * 按"租户 + 用户名"生成锁定 key，用户名忽略大小写，避免通过大小写变体绕过计数。
     */
    private String lockKey(String tenant, String username) {
        return tenant + ":" + username.trim().toLowerCase(Locale.ROOT);
    }

    private void verifyCaptcha(Map<String, Object> parameters) {
        if (captchaService == null) {
            return;
        }
        String captchaId = required(parameters, CAPTCHA_ID);
        String captchaCode = required(parameters, CAPTCHA_CODE);
        if (!captchaService.verify(captchaId, captchaCode)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "invalid_captcha",
                    "captcha is invalid or expired",
                    null
            ));
        }
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

    /**
     * 解析租户标识。优先读取 {@code tenant_id}/{@code tenantId};
     * 为兼容旧客户端,回退读取 {@code tenant_code}/{@code tenantCode}。
     */
    private String optionalTenant(Map<String, Object> parameters) {
        String tenantId = optional(parameters, TENANT_ID);
        if (!tenantId.isBlank()) {
            return tenantId;
        }
        tenantId = optional(parameters, TENANT_ID_CAMEL);
        if (!tenantId.isBlank()) {
            return tenantId;
        }
        String tenantCode = optional(parameters, TENANT_CODE);
        if (!tenantCode.isBlank()) {
            return tenantCode;
        }
        return optional(parameters, TENANT_CODE_CAMEL);
    }

    private String optional(Map<String, Object> parameters, String name) {
        Object value = parameters.get(name);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return "";
    }
}
