package ${package};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 授权服务器启动入口。
 *
 * <p>授权端点（/oauth2/token、/oauth2/jwks、/oauth2/introspect、/oauth2/revoke）由 chaos-auth-server-starter 自动装配。
 * 业务侧只需要提供用户身份适配：实现 {@code ChaosAuthorizationUserService}（本项目在
 * {@code identity.DevIdentityConfiguration} 中提供了仅限开发环境的示例实现）。</p>
 */
@SpringBootApplication
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
