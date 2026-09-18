package com.michael.chaos.test.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.context.RequestContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link WithChaosContext} 注解测试。
 */
@WithChaosContext(tenantId = "tenant-a", userId = "1001")
class WithChaosContextTest {

    /**
     * 类级注解对每个测试方法生效。
     */
    @Test
    void classLevelContextShouldApply() {
        assertThat(RequestContext.tenantId()).isEqualTo("tenant-a");
        assertThat(RequestContext.userId()).isEqualTo("1001");
    }

    /**
     * 方法级注解覆盖类级注解。
     */
    @Test
    @WithChaosContext(tenantId = "tenant-b")
    void methodLevelContextShouldOverrideClassLevel() {
        assertThat(RequestContext.tenantId()).isEqualTo("tenant-b");
        assertThat(RequestContext.userId()).isEmpty();
    }

    /**
     * 未标注注解但注册了扩展的测试，每个方法都从空上下文开始。
     */
    @Nested
    @org.junit.jupiter.api.extension.ExtendWith(ChaosContextExtension.class)
    class WithoutAnnotation {

    /**
     * 没有注解时上下文必须是干净的，避免上一个测试的租户、用户串到下一个测试。
     */
        @Test
        void shouldStartFromEmptyContext() {
            // @Nested 测试的 testClass 为内部类，本身没有注解（@Inherited 只作用于子类，不作用于内部类），扩展按空上下文打开作用域。
            assertThat(RequestContext.tenantId()).isEmpty();
        }
    }
}
