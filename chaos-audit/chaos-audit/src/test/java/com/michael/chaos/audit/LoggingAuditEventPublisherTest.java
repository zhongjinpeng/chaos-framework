package com.michael.chaos.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 日志审计发布器测试。
 */
class LoggingAuditEventPublisherTest {

    /**
     * 日志发布器必须在输出前调用脱敏器。
     */
    @Test
    void shouldSanitizeAttributesBeforeLogging() {
        AtomicReference<Map<String, String>> received = new AtomicReference<>();
        LoggingAuditEventPublisher publisher = new LoggingAuditEventPublisher(attributes -> {
            received.set(attributes);
            return Map.of();
        });

        publisher.publish(AuditEvent.builder(AuditAction.AUTH_LOGIN_FAILURE, AuditOutcome.FAILURE)
                .attributes(Map.of("password", "123456"))
                .build());

        assertThat(received.get()).containsEntry("password", "123456");
    }
}
