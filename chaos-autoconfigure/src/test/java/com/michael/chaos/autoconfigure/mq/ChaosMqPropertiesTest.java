package com.michael.chaos.autoconfigure.mq;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * MQ 配置属性测试。
 */
class ChaosMqPropertiesTest {

    /**
     * outbox 配置被设置为 null 时应恢复默认对象，避免自动装配阶段 NPE。
     */
    @Test
    void shouldRestoreDefaultOutboxWhenSetToNull() {
        ChaosMqProperties properties = new ChaosMqProperties();

        properties.setOutbox(null);

        assertThat(properties.getOutbox()).isNotNull();
        assertThat(properties.getOutbox().getTableName()).isEqualTo("chaos_mq_outbox");
        assertThat(properties.getOutbox().getClaimTimeout()).isEqualTo(Duration.ofMinutes(5));
    }
}
