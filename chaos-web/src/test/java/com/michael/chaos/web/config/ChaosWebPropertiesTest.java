package com.michael.chaos.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Web 配置属性测试。
 */
class ChaosWebPropertiesTest {

    /**
     * 嵌套配置被设置为 null 时应恢复默认对象，避免自动装配阶段 NPE。
     */
    @Test
    void shouldRestoreDefaultNestedPropertiesWhenSetToNull() {
        ChaosWebProperties properties = new ChaosWebProperties();

        assertThat(properties.isRequestTimingEnabled()).isTrue();

        assertThat(properties.isXssEnabled()).isFalse();
        assertThat(properties.getForwarding().isTrustIdentityHeaders()).isFalse();
        assertThat(properties.getForwarding().getTrustedProxies()).isEmpty();

        properties.setRateLimit(null);
        properties.setIdempotent(null);
        properties.setForwarding(null);

        assertThat(properties.getRateLimit()).isNotNull();
        assertThat(properties.getRateLimit().getMaxLocalKeys()).isEqualTo(10_000);
        assertThat(properties.getIdempotent()).isNotNull();
        assertThat(properties.getIdempotent().getMaxLocalKeys()).isEqualTo(10_000);
        assertThat(properties.getForwarding()).isNotNull();
    }
}
