package ${package}.identity;

import com.michael.chaos.authorization.core.ChaosAuthorizationUserService;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * 开发环境身份适配：内置一个演示账号，方便本地联调网关与业务服务。
 *
 * <p>正式接入时请删除本类，改为实现 {@link ChaosAuthorizationUserService} 对接用户中心（密码校验、账号状态、租户、权限）。
 * 本类在生产 profile 下不加载；未提供实现时框架默认拒绝所有登录，不会出现万能口令。</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("!prod & !production & !prd")
public class DevIdentityConfiguration {

    /**
     * 演示账号：用户名 {@code dev.admin-username}（默认 admin），密码 {@code dev.admin-password}（默认 admin123）。
     * 该账号属于租户 tenant-a，拥有 chaos-archetype-web-service 示例接口需要的 todo:read / todo:write 权限。
     */
    @Bean
    public ChaosAuthorizationUserService devAuthorizationUserService(Environment environment) {
        String username = environment.getProperty("dev.admin-username", "admin");
        String password = environment.getProperty("dev.admin-password", "admin123");
        LoginUser admin = new LoginUser("10001", username, "tenant-a", Set.of("admin"), Set.of("todo:read", "todo:write"));
        return new ChaosAuthorizationUserService() {
            @Override
            public LoginUser authenticateByUsername(String inputUsername, String inputPassword) {
                if (username.equals(inputUsername) && password.equals(inputPassword)) {
                    return admin;
                }
                throw new BadCredentialsException("用户名或密码错误");
            }

            @Override
            public LoginUser loadByMobile(String mobile) {
                throw new BadCredentialsException("开发环境未开启短信登录");
            }
        };
    }
}
