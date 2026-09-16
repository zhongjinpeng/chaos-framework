package com.michael.chaos.mq.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.mq.MessageHeaders;
import com.michael.chaos.mq.MessagePublishException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Kafka 发布器发送确认测试。
 */
class KafkaMessagePublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

    /**
     * broker 返回失败时必须抛出异常，不能让 outbox 标记为 SENT。
     */
    @Test
    void shouldThrowWhenBrokerRejectsMessage() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        KafkaMessagePublisher publisher = new KafkaMessagePublisher(kafkaTemplate);

        assertThatThrownBy(() -> publisher.publish(envelope()))
                .isInstanceOf(MessagePublishException.class)
                .hasMessageContaining("broker down");
    }

    /**
     * broker 未在超时时间内确认时应抛出异常。
     */
    @Test
    void shouldThrowWhenSendTimesOut() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
        KafkaMessagePublisher publisher = new KafkaMessagePublisher(kafkaTemplate, Duration.ofMillis(20));

        assertThatThrownBy(() -> publisher.publish(envelope()))
                .isInstanceOf(MessagePublishException.class)
                .hasMessageContaining("timeout");
    }

    /**
     * 发送成功时应携带 message-id 和 message-tag 头。
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldWriteMessageIdHeader() {
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        when(kafkaTemplate.send(captor.capture()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        KafkaMessagePublisher publisher = new KafkaMessagePublisher(kafkaTemplate);

        publisher.publish(envelope());

        ProducerRecord<String, Object> record = captor.getValue();
        assertThat(new String(record.headers().lastHeader(MessageHeaders.MESSAGE_ID).value(), StandardCharsets.UTF_8))
                .isEqualTo("msg-1");
        assertThat(new String(record.headers().lastHeader(MessageHeaders.MESSAGE_TAG).value(), StandardCharsets.UTF_8))
                .isEqualTo("created");
        assertThat(record.key()).isEqualTo("msg-1");
    }

    private static MessageEnvelope<String> envelope() {
        return new MessageEnvelope<>("msg-1", "order.created", "created", "payload", Map.of(), null);
    }
}
