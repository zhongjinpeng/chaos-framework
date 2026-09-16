package com.michael.chaos.test.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.tenant.TenantContext;
import com.michael.chaos.tenant.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 测试上下文夹具测试。
 */
class ChaosTestContextTest {

    @AfterEach
    void cleanUp() {
        ChaosTestContext.clearAll();
    }

    /**
     * 作用域内可以读到租户、用户和 TenantContext；关闭后恢复为空。
     */
    @Test
    void shouldSetAndClearContext() {
        try (ChaosTestContext.Scope ignored = ChaosTestContext.tenant("tenant-a").userId("1001").open()) {
            assertThat(RequestContext.tenantId()).isEqualTo("tenant-a");
            assertThat(RequestContext.userId()).isEqualTo("1001");
            assertThat(TenantContext.tenantId()).isEqualTo("tenant-a");
            assertThat(TenantContext.status()).isEqualTo(TenantStatus.ACTIVE);
        }
        assertThat(RequestContext.current()).isEmpty();
        assertThat(TenantContext.current()).isEmpty();
    }

    /**
     * 嵌套作用域关闭后应恢复外层上下文，而不是直接清空。
     */
    @Test
    void nestedScopeShouldRestoreOuterContext() {
        try (ChaosTestContext.Scope outer = ChaosTestContext.tenant("tenant-a").userId("1001").open()) {
            try (ChaosTestContext.Scope inner = ChaosTestContext.tenant("tenant-b").userId("2002").open()) {
                assertThat(RequestContext.tenantId()).isEqualTo("tenant-b");
                assertThat(TenantContext.tenantId()).isEqualTo("tenant-b");
            }
            assertThat(RequestContext.tenantId()).isEqualTo("tenant-a");
            assertThat(RequestContext.userId()).isEqualTo("1001");
            assertThat(TenantContext.tenantId()).isEqualTo("tenant-a");
        }
        assertThat(RequestContext.current()).isEmpty();
    }

    /**
     * 非法租户 ID 与生产行为一致，会被白名单校验置空。
     */
    @Test
    void shouldApplyProductionIdentifierValidation() {
        try (ChaosTestContext.Scope ignored = ChaosTestContext.tenant("x' OR '1'='1").open()) {
            assertThat(RequestContext.tenantId()).isEmpty();
            assertThat(TenantContext.current()).isEmpty();
        }
    }

    /**
     * 重复关闭作用域是安全的。
     */
    @Test
    void closeShouldBeIdempotent() {
        ChaosTestContext.Scope scope = ChaosTestContext.tenant("tenant-a").open();
        scope.close();
        try (ChaosTestContext.Scope ignored = ChaosTestContext.tenant("tenant-b").open()) {
            scope.close();
            assertThat(RequestContext.tenantId()).isEqualTo("tenant-b");
        }
    }
}
