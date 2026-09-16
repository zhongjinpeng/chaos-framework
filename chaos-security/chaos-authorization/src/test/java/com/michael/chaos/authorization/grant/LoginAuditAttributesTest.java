package com.michael.chaos.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoginAuditAttributesTest {

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

    @Test
    void shouldResolveTenantUsingPasswordGrantCompatibilityOrder() {
        Map<String, Object> parameters = Map.of(
                "tenantId", "tenant-id",
                "tenant_code", "tenant-code"
        );

        assertThat(LoginAuditAttributes.tenant(parameters)).isEqualTo("tenant-id");
    }
}
