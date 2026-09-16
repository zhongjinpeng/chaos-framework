package com.michael.chaos.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 审计事件模型测试。
 */
class AuditEventTest {

    /**
     * 审计事件应规范化空字段并复制扩展属性。
     */
    @Test
    void shouldNormalizeAndCopyAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("grantType", "password");

        AuditEvent event = AuditEvent.builder(" auth.login.success ", AuditOutcome.SUCCESS)
                .principalId(null)
                .tenantId(" tenant-a ")
                .attributes(attributes)
                .build();
        attributes.put("grantType", "sms_code");

        assertThat(event.action()).isEqualTo("auth.login.success");
        assertThat(event.principalId()).isEmpty();
        assertThat(event.tenantId()).isEqualTo("tenant-a");
        assertThat(event.attributes()).containsEntry("grantType", "password");
    }

    /**
     * 动作编码不能为空。
     */
    @Test
    void shouldRejectBlankAction() {
        assertThatThrownBy(() -> AuditEvent.builder(" ", AuditOutcome.SUCCESS).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");
    }
}
