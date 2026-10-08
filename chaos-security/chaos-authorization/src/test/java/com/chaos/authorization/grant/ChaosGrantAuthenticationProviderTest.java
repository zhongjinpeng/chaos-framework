package com.chaos.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

class ChaosGrantAuthenticationProviderTest {

    @Test
    void shouldUseOriginalAuthenticationFailureReasonForAudit() {
        BadCredentialsException cause = new BadCredentialsException("密码错误");
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_GRANT,
                        "username or password is invalid",
                        null
                ),
                cause
        );

        assertThat(ChaosGrantAuthenticationProvider.loginFailureReason(exception))
                .isEqualTo("密码错误");
    }

    @Test
    void shouldKeepDirectAuthenticationFailureReasonForAudit() {
        BadCredentialsException exception = new BadCredentialsException("客户端不允许使用密码登录");

        assertThat(ChaosGrantAuthenticationProvider.loginFailureReason(exception))
                .isEqualTo("客户端不允许使用密码登录");
    }
}
