package com.michael.chaos.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoginAuditAttributesTest {

    /**
     * 登录审计绝不能记录密码、验证码、客户端密钥——这是审计表最容易泄密的地方。
     */
    @Test
    void shouldKeepOnlyNonSensitiveLoginAttributes() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("tenant_id", "tenant-1");
        parameters.put("username", "admin");
        parameters.put("password", "never-persist-this");
        parameters.put("refresh_token", "never-persist-this-either");
        AuthorizationLoginContext context = new AuthorizationLoginContext(
                "password", "device-1", "127.0.0.1", "JUnit");

        Map<String, String> attributes = LoginAuditAttributes.from(parameters, context);

        assertThat(attributes).containsEntry("username", "admin")
                .containsEntry("grantType", "password")
                .containsEntry("deviceId", "device-1")
                .containsEntry("userAgent", "JUnit")
                .doesNotContainKeys("password", "refresh_token");
    }

    /**
     * 租户参数有四种历史写法，取值顺序要和密码模式登录保持一致，否则审计记的租户和实际登录的不是一个。
     */
    @Test
    void shouldResolveTenantUsingPasswordGrantCompatibilityOrder() {
        Map<String, Object> parameters = Map.of(
                "tenantId", "tenant-id",
                "tenant_code", "tenant-code"
        );

        assertThat(LoginAuditAttributes.tenant(parameters)).isEqualTo("tenant-id");
    }
}
