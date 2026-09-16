package com.michael.chaos.test.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.security.api.auth.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全测试辅助测试。
 */
class ChaosSecurityTestSupportTest {

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        RequestContext.clear();
    }

    /**
     * Authentication 的 principal 与 authority 映射应与生产 JWT 转换器保持一致。
     */
    @Test
    void authenticationShouldFollowProductionConventions() {
        LoginUser user = TestLoginUsers.user("1001").tenant("tenant-a").roles("admin").permissions("order:read").build();

        Authentication authentication = ChaosSecurityTestSupport.authentication(user);

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(user);
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_admin", "order:read");
    }

    /**
     * 作用域内写入安全上下文和请求上下文，关闭后恢复。
     */
    @Test
    void withLoginUserShouldSetAndRestoreContexts() {
        LoginUser user = TestLoginUsers.user("1001").tenant("tenant-a").build();

        try (ChaosSecurityTestSupport.Scope ignored = ChaosSecurityTestSupport.withLoginUser(user)) {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(user);
            assertThat(RequestContext.userId()).isEqualTo("1001");
            assertThat(RequestContext.tenantId()).isEqualTo("tenant-a");
        }

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(RequestContext.current()).isEmpty();
    }

    /**
     * 用户名默认等于用户 ID，集合做了防御性复制。
     */
    @Test
    void builderShouldApplyDefaults() {
        LoginUser user = TestLoginUsers.user("1001").build();

        assertThat(user.username()).isEqualTo("1001");
        assertThat(user.tenantId()).isEmpty();
        assertThat(user.roles()).isEmpty();
    }
}
