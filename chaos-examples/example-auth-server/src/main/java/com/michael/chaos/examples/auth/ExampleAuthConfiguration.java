package com.michael.chaos.examples.auth;

import com.michael.chaos.authorization.core.ChaosAuthorizationUserService;
import com.michael.chaos.authorization.sms.SmsCodeVerifier;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * 示例授权服务器的业务身份适配。
 *
 * <p>真实业务系统应在这里替换为用户中心、密码策略、短信平台和风控服务。</p>
 *
 * <p>示例中的明文密码和固定短信验证码只能用于本地演示，因此在 prod/production profile 下不加载，
 * 避免被原样复制到生产环境形成万能口令。</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("!prod & !production")
public class ExampleAuthConfiguration {

    private static final String EXAMPLE_SMS_CODE = "123456";

    private static final Map<String, ExampleUser> USERS = Map.of(
            "admin", new ExampleUser(
                    "10001",
                    "admin",
                    "123456",
                    "13800000000",
                    "tenant-a",
                    Set.of("admin"),
                    Set.of("order:read", "order:create", "order:cancel")
            ),
            "operator", new ExampleUser(
                    "10002",
                    "operator",
                    "123456",
                    "13900000000",
                    "tenant-b",
                    Set.of("operator"),
                    Set.of("order:read")
            )
    );

    /**
     * 注册示例用户认证服务，演示用户名密码和手机号验证码两种登录方式。
     */
    @Bean
    public ChaosAuthorizationUserService chaosAuthorizationUserService() {
        return new ChaosAuthorizationUserService() {
            @Override
            public LoginUser authenticateByUsername(String username, String password) {
                ExampleUser user = USERS.get(username);
                if (user == null || !user.password().equals(password)) {
                    throw new BadCredentialsException("用户名或密码错误");
                }
                return user.toLoginUser();
            }

            @Override
            public LoginUser loadByMobile(String mobile) {
                return USERS.values().stream()
                        .filter(user -> user.mobile().equals(mobile))
                        .findFirst()
                        .map(ExampleUser::toLoginUser)
                        .orElseThrow(() -> new BadCredentialsException("手机号未绑定用户"));
            }
        };
    }

    /**
     * 注册示例短信验证码校验器。
     */
    @Bean
    public SmsCodeVerifier smsCodeVerifier() {
        return (mobile, code) -> EXAMPLE_SMS_CODE.equals(code);
    }

    /**
     * 示例用户模型。
     */
    private record ExampleUser(
            String userId,
            String username,
            String password,
            String mobile,
            String tenantId,
            Set<String> roles,
            Set<String> permissions
    ) {

        /**
         * 转换为 framework 统一登录用户。
         */
        private LoginUser toLoginUser() {
            return new LoginUser(userId, username, tenantId, roles, permissions);
        }
    }
}
