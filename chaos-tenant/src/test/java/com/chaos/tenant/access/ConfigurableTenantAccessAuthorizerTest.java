package com.chaos.tenant.access;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 可配置租户访问策略测试。
 */
class ConfigurableTenantAccessAuthorizerTest {

    private final ConfigurableTenantAccessAuthorizer authorizer =
            new ConfigurableTenantAccessAuthorizer(
                    "platform"::equals,
                    Map.of(
                            "PLATFORM", ConfigurableTenantAccessAuthorizer.PolicyKind.PLATFORM,
                            "ORG", ConfigurableTenantAccessAuthorizer.PolicyKind.ORGANIZATION,
                            "SHARED", ConfigurableTenantAccessAuthorizer.PolicyKind.SHARED,
                            "PUBLIC", ConfigurableTenantAccessAuthorizer.PolicyKind.PUBLIC));

    /**
     * 平台、机构、共享与公开策略按租户类型判定。
     */
    @Test
    void shouldEvaluatePolicyKinds() {
        assertTrue(authorizer.isAllowed("PLATFORM", "platform"));
        assertFalse(authorizer.isAllowed("PLATFORM", "org-1"));
        assertTrue(authorizer.isAllowed("ORG", "org-1"));
        assertFalse(authorizer.isAllowed("ORG", "platform"));
        assertTrue(authorizer.isAllowed("SHARED", "org-1"));
        assertFalse(authorizer.isAllowed("SHARED", " "));
        assertTrue(authorizer.isAllowed("PUBLIC", null));
    }

    /**
     * 未登记的策略名必须显式失败，避免拼写错误被静默放行。
     */
    @Test
    void shouldRejectUnknownPolicy() {
        assertThrows(IllegalStateException.class, () -> authorizer.isAllowed("UNKNOWN", "org-1"));
    }
}
