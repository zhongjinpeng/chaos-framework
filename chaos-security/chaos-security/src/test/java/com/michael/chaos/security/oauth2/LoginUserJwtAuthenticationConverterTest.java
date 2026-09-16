package com.michael.chaos.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.security.api.auth.ChaosJwtClaims;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * LoginUser JWT 转换测试。
 */
class LoginUserJwtAuthenticationConverterTest {

    private final LoginUserJwtAuthenticationConverter converter = new LoginUserJwtAuthenticationConverter();

    /**
     * 使用 chaos 授权中心 claim 构造 LoginUser。
     */
    @Test
    void convertShouldReadChaosClaims() {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .claim(ChaosJwtClaims.USER_ID, "1001")
                .claim(ChaosJwtClaims.USERNAME, "michael")
                .claim(ChaosJwtClaims.TENANT_ID, "tenant-a")
                .claim(ChaosJwtClaims.ROLES, List.of("admin"))
                .claim(ChaosJwtClaims.PERMISSIONS, List.of("order:read"))
                .build();

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();

        assertThat(loginUser.userId()).isEqualTo("1001");
        assertThat(loginUser.username()).isEqualTo("michael");
        assertThat(loginUser.tenantId()).isEqualTo("tenant-a");
        assertThat(loginUser.roles()).containsExactly("admin");
        assertThat(loginUser.permissions()).containsExactly("order:read");
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_admin", "order:read");
    }

    /**
     * 非 chaos JWT 没有 username 时，使用 subject 兜底。
     */
    @Test
    void convertShouldFallbackToSubject() {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject("1001")
                .claim(ChaosJwtClaims.ROLES, "admin, auditor")
                .claim(ChaosJwtClaims.PERMISSIONS, "order:read,order:write")
                .build();

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();

        assertThat(loginUser.userId()).isEqualTo("1001");
        assertThat(loginUser.username()).isEqualTo("1001");
        assertThat(loginUser.roles()).containsExactlyInAnyOrder("admin", "auditor");
        assertThat(loginUser.permissions()).containsExactlyInAnyOrder("order:read", "order:write");
    }
}
