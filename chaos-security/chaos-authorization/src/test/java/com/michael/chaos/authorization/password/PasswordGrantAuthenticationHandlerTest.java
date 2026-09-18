package com.michael.chaos.authorization.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.authorization.captcha.CaptchaChallenge;
import com.michael.chaos.authorization.captcha.CaptchaService;
import com.michael.chaos.authorization.core.ChaosAuthorizationUserService;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

class PasswordGrantAuthenticationHandlerTest {

    /**
     * 多租户下同名用户可以存在于不同租户，租户标识必须传到业务身份服务，否则会认成别的租户的同名用户。
     */
    @Test
    void shouldPassTenantIdToUserService() {
        CapturingUserService userService = new CapturingUserService();
        PasswordGrantAuthenticationHandler handler = new PasswordGrantAuthenticationHandler(userService);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("tenant_id", "1910000000000000001");
        parameters.put("username", "admin");
        parameters.put("password", "123456");

        LoginUser user = handler.authenticate(parameters);

        assertThat(user.tenantId()).isEqualTo("1910000000000000001");
        assertThat(userService.tenant).isEqualTo("1910000000000000001");
        assertThat(userService.username).isEqualTo("admin");
        assertThat(userService.password).isEqualTo("123456");
    }

    /**
     * OAuth2 请求参数惯例是下划线，前端框架常写成驼峰；两种写法都要认，避免接入时莫名其妙缺租户。
     */
    @Test
    void shouldSupportCamelTenantId() {
        CapturingUserService userService = new CapturingUserService();
        PasswordGrantAuthenticationHandler handler = new PasswordGrantAuthenticationHandler(userService);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("tenantId", "1910000000000000001");
        parameters.put("username", "admin");
        parameters.put("password", "123456");

        handler.authenticate(parameters);

        assertThat(userService.tenant).isEqualTo("1910000000000000001");
    }

    /**
     * 同时传了 ID 和 code 时以 ID 为准：code 可变，ID 才是稳定标识。
     */
    @Test
    void shouldPreferTenantIdOverTenantCode() {
        CapturingUserService userService = new CapturingUserService();
        PasswordGrantAuthenticationHandler handler = new PasswordGrantAuthenticationHandler(userService);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("tenant_id", "1910000000000000001");
        parameters.put("tenant_code", "demo");
        parameters.put("username", "admin");
        parameters.put("password", "123456");

        handler.authenticate(parameters);

        assertThat(userService.tenant).isEqualTo("1910000000000000001");
    }

    /**
     * 老客户端只会传 tenant_code，不能因为换了参数名就登录不了。
     */
    @Test
    void shouldFallBackToTenantCodeForLegacyClients() {
        CapturingUserService userService = new CapturingUserService();
        PasswordGrantAuthenticationHandler handler = new PasswordGrantAuthenticationHandler(userService);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("tenant_code", "demo");
        parameters.put("username", "admin");
        parameters.put("password", "123456");

        handler.authenticate(parameters);

        assertThat(userService.tenant).isEqualTo("demo");
    }

    /**
     * 缺密码必须直接拒绝，不能带着空密码去调用业务身份服务。
     */
    @Test
    void shouldRejectMissingPassword() {
        PasswordGrantAuthenticationHandler handler = new PasswordGrantAuthenticationHandler(new CapturingUserService());
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("username", "admin");

        assertThatThrownBy(() -> handler.authenticate(parameters))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("parameter is required: password");
    }

    /**
     * 验证码要在查库之前校验，否则撞库攻击仍然能通过登录接口探测用户是否存在。
     */
    @Test
    void shouldVerifyCaptchaBeforeAuthenticatingUser() {
        CapturingUserService userService = new CapturingUserService();
        CapturingCaptchaService captchaService = new CapturingCaptchaService(true);
        PasswordGrantAuthenticationHandler handler = new PasswordGrantAuthenticationHandler(userService, captchaService);
        Map<String, Object> parameters = passwordParameters();
        parameters.put("captcha_id", "challenge-1");
        parameters.put("captcha_code", "ABCD");

        handler.authenticate(parameters);

        assertThat(captchaService.captchaId).isEqualTo("challenge-1");
        assertThat(captchaService.answer).isEqualTo("ABCD");
        assertThat(userService.username).isEqualTo("admin");
    }

    /**
     * 验证码错误按认证失败处理，不泄露是验证码错还是账号密码错。
     */
    @Test
    void shouldRejectInvalidCaptcha() {
        PasswordGrantAuthenticationHandler handler = new PasswordGrantAuthenticationHandler(
                new CapturingUserService(), new CapturingCaptchaService(false));
        Map<String, Object> parameters = passwordParameters();
        parameters.put("captcha_id", "challenge-1");
        parameters.put("captcha_code", "WRONG");

        assertThatThrownBy(() -> handler.authenticate(parameters))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_captcha"));
    }

    /**
     * 开启验证码后缺少验证码参数必须拒绝，不能退化成「没传就不校验」。
     */
    @Test
    void shouldRequireCaptchaParametersWhenCaptchaIsEnabled() {
        PasswordGrantAuthenticationHandler handler = new PasswordGrantAuthenticationHandler(
                new CapturingUserService(), new CapturingCaptchaService(true));

        assertThatThrownBy(() -> handler.authenticate(passwordParameters()))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, ex -> {
                    assertThat(ex.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_REQUEST);
                    assertThat(ex.getMessage()).contains("captcha_id");
                });
    }

    /**
     * 连续认证失败达到阈值后必须锁定，锁定期内不再调用业务身份服务。
     */
    @Test
    void shouldLockAccountAfterRepeatedFailures() {
        FailingUserService userService = new FailingUserService();
        PasswordGrantAuthenticationHandler handler = new PasswordGrantAuthenticationHandler(
                userService, null, new InMemoryLoginFailureLimiter(2, java.time.Duration.ofMinutes(15), 100));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> handler.authenticate(passwordParameters()))
                    .isInstanceOf(OAuth2AuthenticationException.class);
        }
        Map<String, Object> caseVariant = passwordParameters();
        caseVariant.put("username", "ADMIN");

        assertThatThrownBy(() -> handler.authenticate(caseVariant))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getDescription()).contains("too many failed login attempts"));
        assertThat(userService.calls).isEqualTo(2);
    }

    private static class FailingUserService implements ChaosAuthorizationUserService {

        private int calls;

        @Override
        public LoginUser authenticateByUsername(String username, String password) {
            calls++;
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        @Override
        public LoginUser loadByMobile(String mobile) {
            return null;
        }
    }

    private Map<String, Object> passwordParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("username", "admin");
        parameters.put("password", "123456");
        return parameters;
    }

    private static class CapturingCaptchaService implements CaptchaService {

        private final boolean valid;

        private String captchaId;

        private String answer;

        private CapturingCaptchaService(boolean valid) {
            this.valid = valid;
        }

        @Override
        public CaptchaChallenge create() {
            return null;
        }

        @Override
        public boolean verify(String captchaId, String answer) {
            this.captchaId = captchaId;
            this.answer = answer;
            return valid;
        }
    }

    private static class CapturingUserService implements ChaosAuthorizationUserService {

        private String tenant;

        private String username;

        private String password;

        @Override
        public LoginUser authenticateByUsername(String username, String password) {
            return authenticateByUsername("", username, password);
        }

        @Override
        public LoginUser authenticateByUsername(String tenant, String username, String password) {
            this.tenant = tenant;
            this.username = username;
            this.password = password;
            return new LoginUser("1", username, tenant, Set.of("admin"), Set.of("sys:user:list"));
        }

        @Override
        public LoginUser loadByMobile(String mobile) {
            return null;
        }
    }
}
