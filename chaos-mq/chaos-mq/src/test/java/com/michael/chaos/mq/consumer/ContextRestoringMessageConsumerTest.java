package com.michael.chaos.mq.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.trace.TraceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 消费端上下文还原测试。
 */
class ContextRestoringMessageConsumerTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    /**
     * 消费期间应能读到消息头中的 trace 和租户，结束后清理。
     */
    @Test
    void shouldRestoreContextDuringConsumeAndClearAfterwards() {
        List<String> observed = new ArrayList<>();
        ContextRestoringMessageConsumer<String> consumer = new ContextRestoringMessageConsumer<>(message -> {
            observed.add(TraceContext.traceId());
            observed.add(RequestContext.tenantId());
            observed.add(RequestContext.userId());
        }, "order-service");

        consumer.consume(new MessageEnvelope<>("msg-1", "order.created", "", "payload", Map.of(
                ChaosHeaders.TRACE_ID, "4bf92f3577b34da6a3ce929d0e0e4736",
                ChaosHeaders.TENANT_ID, "tenant-a",
                ChaosHeaders.USER_ID, "10001"
        ), null));

        assertThat(observed).containsExactly("4bf92f3577b34da6a3ce929d0e0e4736", "tenant-a", "10001");
        assertThat(RequestContext.current()).isEmpty();
    }

    /**
     * 消费异常时也要恢复原上下文。
     */
    @Test
    void shouldRestoreContextWhenConsumeFails() {
        ContextRestoringMessageConsumer<String> consumer = new ContextRestoringMessageConsumer<>(message -> {
            throw new IllegalStateException("boom");
        }, "order-service");

        assertThatThrownBy(() -> consumer.consume(new MessageEnvelope<>("msg-2", "order.created", "", "payload",
                Map.of(ChaosHeaders.TENANT_ID, "tenant-b"), null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(RequestContext.current()).isEmpty();
    }
}
