package com.chaos.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chaos.core.exception.BizException;
import com.chaos.core.exception.CommonErrorCode;
import com.chaos.security.api.auth.LoginUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具测试。
 */
class LoginUserUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExposeCurrentUserInformation() {
        LoginUser user = new LoginUser(
                "1001", "alice", "tenant-a", Set.of("admin"), Set.of("order:read"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));

        assertThat(LoginUserUtils.currentUser()).contains(user);
        assertThat(LoginUserUtils.requiredUser()).isEqualTo(user);
        assertThat(LoginUserUtils.requiredUserId()).isEqualTo("1001");
        assertThat(LoginUserUtils.requiredTenantId()).isEqualTo("tenant-a");
        assertThat(LoginUserUtils.requiredUsernameOrUserId()).isEqualTo("alice");
        assertThat(LoginUserUtils.userId()).isEqualTo("1001");
        assertThat(LoginUserUtils.username()).isEqualTo("alice");
        assertThat(LoginUserUtils.tenantId()).isEqualTo("tenant-a");
        assertThat(LoginUserUtils.roles()).containsExactly("admin");
        assertThat(LoginUserUtils.permissions()).containsExactly("order:read");
    }

    @Test
    void shouldReturnEmptyValuesWhenUnauthenticated() {
        assertThat(LoginUserUtils.currentUser()).isEmpty();
        assertThat(LoginUserUtils.userId()).isEmpty();
        assertThat(LoginUserUtils.username()).isEmpty();
        assertThat(LoginUserUtils.tenantId()).isEmpty();
        assertThat(LoginUserUtils.roles()).isEmpty();
        assertThat(LoginUserUtils.permissions()).isEmpty();
        assertThatThrownBy(LoginUserUtils::requiredUser)
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void shouldRejectMissingRequiredIdentityFields() {
        LoginUser user = new LoginUser(null, " ", null, Set.of(), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));

        assertThatThrownBy(LoginUserUtils::requiredUserId)
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        assertThatThrownBy(LoginUserUtils::requiredTenantId)
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThatThrownBy(LoginUserUtils::requiredUsernameOrUserId)
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void shouldFallBackToUserIdWhenUsernameIsBlank() {
        LoginUser user = new LoginUser("1001", " ", "tenant-a", Set.of(), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));

        assertThat(LoginUserUtils.requiredUsernameOrUserId()).isEqualTo("1001");
    }
}
