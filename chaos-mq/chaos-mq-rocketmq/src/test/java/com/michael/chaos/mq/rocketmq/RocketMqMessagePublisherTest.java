package com.michael.chaos.mq.rocketmq;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.mq.MessagePublishException;
import java.util.Map;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

/**
 * RocketMQ 发布器发送确认测试。
 */
class RocketMqMessagePublisherTest {

    private final RocketMQTemplate template = mock(RocketMQTemplate.class);

    /**
     * SEND_OK 时正常返回。
     */
    @Test
    void shouldAcceptSendOk() {
        when(template.syncSend(eq("order.created:created"), any(Message.class), anyLong())).thenReturn(result(SendStatus.SEND_OK));

        assertThatCode(() -> new RocketMqMessagePublisher(template).publish(envelope())).doesNotThrowAnyException();
    }

    /**
     * 非 SEND_OK 状态必须视为失败。
     */
    @Test
    void shouldRejectNonOkStatus() {
        when(template.syncSend(any(String.class), any(Message.class), anyLong()))
                .thenReturn(result(SendStatus.FLUSH_DISK_TIMEOUT));

        assertThatThrownBy(() -> new RocketMqMessagePublisher(template).publish(envelope()))
                .isInstanceOf(MessagePublishException.class)
                .hasMessageContaining("FLUSH_DISK_TIMEOUT");
    }

    /**
     * 发送异常应包装为 MessagePublishException。
     */
    @Test
    void shouldWrapSendException() {
        when(template.syncSend(any(String.class), any(Message.class), anyLong()))
                .thenThrow(new IllegalStateException("nameserver unavailable"));

        assertThatThrownBy(() -> new RocketMqMessagePublisher(template).publish(envelope()))
                .isInstanceOf(MessagePublishException.class)
                .hasMessageContaining("nameserver unavailable");
    }

    private static SendResult result(SendStatus status) {
        SendResult result = new SendResult();
        result.setSendStatus(status);
        return result;
    }

    private static MessageEnvelope<String> envelope() {
        return new MessageEnvelope<>("msg-1", "order.created", "created", "payload", Map.of(), null);
    }
}
