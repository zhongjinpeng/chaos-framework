package com.michael.chaos.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.security.api.auth.ChaosJwtClaims;
import com.michael.chaos.security.api.auth.LoginUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;

/**
 * opaque token 到 LoginUser 的转换测试。
 */
class LoginUserOpaqueTokenAuthenticationConverterTest {

    private final LoginUserOpaqueTokenAuthenticationConverter converter =
            new LoginUserOpaqueTokenAuthenticationConverter();

    /**
     * introspection 结果要转成与 JWT 模式一致的 LoginUser，业务代码才不用关心用的是哪种 token。
     */
    @Test
    void convertShouldBuildLoginUserPrincipalFromIntrospectionClaims() {
        OAuth2AuthenticatedPrincipal principal = new DefaultOAuth2AuthenticatedPrincipal(Map.of(
                OAuth2TokenIntrospectionClaimNames.SUB, "10001",
                OAuth2TokenIntrospectionClaimNames.EXP, Instant.now().plusSeconds(3600),
                OAuth2TokenIntrospectionClaimNames.IAT, Instant.now(),
                ChaosJwtClaims.USER_ID, "10001",
                ChaosJwtClaims.USERNAME, "admin",
                ChaosJwtClaims.TENANT_ID, "tenant-a",
                ChaosJwtClaims.ROLES, List.of("admin"),
                ChaosJwtClaims.PERMISSIONS, List.of("order:read")
        ), List.of());

        Object authenticationPrincipal = converter.convert("opaque-token", principal).getPrincipal();

        assertThat(authenticationPrincipal).isInstanceOf(LoginUserOpaqueTokenPrincipal.class);
        LoginUser loginUser = ((LoginUserOpaqueTokenPrincipal) authenticationPrincipal).loginUser();
        assertThat(loginUser.userId()).isEqualTo("10001");
        assertThat(loginUser.username()).isEqualTo("admin");
        assertThat(loginUser.tenantId()).isEqualTo("tenant-a");
        assertThat(loginUser.roles()).containsExactly("admin");
        assertThat(loginUser.permissions()).containsExactly("order:read");
    }
}
