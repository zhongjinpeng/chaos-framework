package com.michael.chaos.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 租户访问校验器测试。
 */
class TenantAccessValidatorTest {

    /**
     * fail-closed 开启时，缺失租户 ID 应拒绝访问。
     */
    @Test
    void shouldDenyMissingTenantWhenFailClosed() {
        TenantAccessValidator validator = new TenantAccessValidator(new NoopTenantStatusProvider(), true);

        TenantAccessDecision decision = validator.validate("");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("missing");
    }

    /**
     * 租户状态正常时应允许访问。
     */
    @Test
    void shouldAllowActiveTenant() {
        TenantAccessValidator validator = new TenantAccessValidator(new NoopTenantStatusProvider(), true);

        TenantAccessDecision decision = validator.validate("tenant-a");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.tenant().status()).isEqualTo(TenantStatus.ACTIVE);
    }

    /**
     * 冻结租户在 fail-closed 模式下应拒绝访问。
     */
    @Test
    void shouldDenyFrozenTenantWhenFailClosed() {
        TenantStatusProvider provider = tenantId -> new TenantDescriptor(
                tenantId,
                TenantStatus.FROZEN,
                TenantPlan.empty(),
                TenantIsolationMode.SHARED_SCHEMA
        );
        TenantAccessValidator validator = new TenantAccessValidator(provider, true);

        TenantAccessDecision decision = validator.validate("tenant-a");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains(TenantStatus.FROZEN.name());
    }
}
