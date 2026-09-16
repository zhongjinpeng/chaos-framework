package com.michael.chaos.test.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.michael.chaos.security.api.auth.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MockMvc 登录用户后置处理器测试。
 */
class ChaosMockMvcSecurityTest {

    /**
     * 模拟应用中的 Spring Security 过滤器链：{@code @WebMvcTest} / {@code @SpringBootTest} 会自动应用，
     * standalone MockMvc 需要显式 {@code apply(springSecurity(...))}。
     */
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WhoAmIController())
            .apply(springSecurity(new FilterChainProxy(new DefaultSecurityFilterChain(
                    AnyRequestMatcher.INSTANCE,
                    new SecurityContextHolderFilter(new RequestAttributeSecurityContextRepository())))))
            .build();

    /**
     * 请求处理线程中应能读到 LoginUser principal。
     */
    @Test
    void requestShouldRunAsLoginUser() throws Exception {
        LoginUser user = TestLoginUsers.user("1001").tenant("tenant-a").build();

        mockMvc.perform(get("/whoami").with(ChaosMockMvcSecurity.loginUser(user)))
                .andExpect(status().isOk())
                .andExpect(content().string("1001@tenant-a"));
    }

    @RestController
    static class WhoAmIController {

        @GetMapping("/whoami")
        String whoAmI() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            LoginUser user = (LoginUser) authentication.getPrincipal();
            return user.userId() + "@" + user.tenantId();
        }
    }
}
