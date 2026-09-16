package com.michael.chaos.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.security.api.access.AuthorizationDecision;
import com.michael.chaos.security.api.access.AuthorizationPolicy;
import com.michael.chaos.security.api.access.DefaultAuthorizationManager;
import com.michael.chaos.security.api.access.PermissionAuthorizationService;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityUtils 测试。
 */
class SecurityUtilsTest {

    @AfterEach
    void clear() {
        SecurityUtils.setPermissionAuthorizationService(null);
        SecurityContextHolder.clearContext();
    }

    /**
     * 注册授权服务后 hasPermission 必须与授权管理器（含 DENY 策略）判定一致。
     */
    @Test
    void shouldDelegateToAuthorizationManager() {
        LoginUser user = new LoginUser("1001", "alice", "tenant-a", Set.of(), Set.of("order:delete"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
        assertThat(SecurityUtils.hasPermission("order:delete")).isTrue();

        AuthorizationPolicy denyAll = new AuthorizationPolicy() {
            @Override
            public String id() {
                return "deny-all";
            }

            @Override
            public AuthorizationDecision decide(com.michael.chaos.security.api.access.AuthorizationRequest request) {
                return AuthorizationDecision.deny("deny-all", "blocked");
            }
        };
        SecurityUtils.setPermissionAuthorizationService(
                new PermissionAuthorizationService(new DefaultAuthorizationManager(List.of(denyAll))));

        assertThat(SecurityUtils.hasPermission("order:delete")).isFalse();
    }
}
