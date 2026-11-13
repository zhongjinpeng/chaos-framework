package com.chaos.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 平台租户上下文测试。
 */
class PlatformTenantContextTest {

    /**
     * 当前租户读取器决定平台判定与机构租户返回。
     */
    @Test
    void shouldResolveTenantFromSupplier() {
        PlatformTenantContext context = new PlatformTenantContext("platform", () -> "org-1");

        assertEquals("platform", context.platformTenantId());
        assertEquals("org-1", context.currentTenantId());
        assertFalse(context.isPlatformTenant());
        assertTrue(context.isPlatformTenant("platform"));
        assertEquals("org-1", context.requireOrganizationTenant(IllegalStateException::new));
        assertThrows(IllegalStateException.class,
                () -> context.requirePlatformTenant(IllegalStateException::new));
    }

    /**
     * 平台租户访问平台专属能力时通过校验。
     */
    @Test
    void shouldAllowPlatformTenant() {
        PlatformTenantContext context = new PlatformTenantContext("platform", () -> "platform");

        assertTrue(context.isPlatformTenant());
        context.requirePlatformTenant(IllegalStateException::new);
        assertThrows(IllegalStateException.class,
                () -> context.requireOrganizationTenant(IllegalStateException::new));
    }
}
