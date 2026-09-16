package com.michael.chaos.test.security;

import com.michael.chaos.security.api.auth.LoginUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc 请求级登录用户。
 *
 * <pre>{@code
 * mockMvc.perform(get("/api/orders").with(ChaosMockMvcSecurity.loginUser(
 *         TestLoginUsers.user("1001").tenant("tenant-a").permissions("order:read").build())))
 *        .andExpect(status().isOk());
 * }</pre>
 *
 * <p>MockMvc 必须挂载 Spring Security 过滤器链才能把身份传到请求线程：{@code @WebMvcTest}、
 * {@code @SpringBootTest + @AutoConfigureMockMvc} 会自动挂载；手动构建时需要
 * {@code apply(SecurityMockMvcConfigurers.springSecurity())}。</p>
 *
 * <p>需要项目 test classpath 中存在 spring-security-test。与直接伪造 JWT 相比，这里跳过 token 解析，
 * 只验证业务接口在“已认证为某个 LoginUser”时的行为；token 解析本身应由安全模块的测试覆盖。</p>
 */
public final class ChaosMockMvcSecurity {

    private ChaosMockMvcSecurity() {
    }

    /**
     * 以指定 LoginUser 身份发起请求。
     */
    public static RequestPostProcessor loginUser(LoginUser user) {
        return SecurityMockMvcRequestPostProcessors.authentication(ChaosSecurityTestSupport.authentication(user));
    }
}
