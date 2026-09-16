package com.michael.chaos.test.boot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 生产安全测试辅助测试。
 */
class ProductionSafetyTestSupportTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    /**
     * 生产模式属性应写入环境。
     */
    @Test
    void productionModeShouldSetProperty() {
        ProductionSafetyTestSupport.productionMode(runner).run(context -> {
            assertThat(context.getEnvironment().getProperty("chaos.production-safety.production-mode")).isEqualTo("true");
            assertThat(context.getEnvironment().getProperty("chaos.production-safety.fail-fast")).isNull();
        });
    }

    /**
     * 只告警模式同时关闭 fail-fast。
     */
    @Test
    void warnOnlyShouldDisableFailFast() {
        ProductionSafetyTestSupport.productionModeWarnOnly(runner).run(context ->
                assertThat(context.getEnvironment().getProperty("chaos.production-safety.fail-fast")).isEqualTo("false"));
    }
}
