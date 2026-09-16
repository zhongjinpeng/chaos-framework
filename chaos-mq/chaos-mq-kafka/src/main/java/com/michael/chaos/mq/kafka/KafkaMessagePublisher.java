package com.michael.chaos.mq.kafka;

import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.mq.MessageHeaders;
import com.michael.chaos.mq.MessagePublishException;
import com.michael.chaos.mq.MessagePublisher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 基于 KafkaTemplate 的消息发布器。
 *
 * <p>{@code KafkaTemplate#send} 是异步的，旧实现丢弃返回的 future，broker 不可用时 outbox 仍会被标记为 SENT。
 * 当前实现同步等待 broker 确认，失败或超时抛出 {@link MessagePublishException}，由调用方决定重试。</p>
 */
public class KafkaMessagePublisher implements MessagePublisher {

    /**
     * 默认发送确认超时时间。
     */
    public static final Duration DEFAULT_SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final Duration sendTimeout;

    /**
     * 创建默认 10 秒确认超时的 Kafka 消息发布器。
     */
    public KafkaMessagePublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this(kafkaTemplate, DEFAULT_SEND_TIMEOUT);
    }

    /**
     * 创建指定确认超时的 Kafka 消息发布器。
     *
     * @param kafkaTemplate Kafka 模板
     * @param sendTimeout 等待 broker 确认的最长时间
     */
    public KafkaMessagePublisher(KafkaTemplate<String, Object> kafkaTemplate, Duration sendTimeout) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        this.sendTimeout = sendTimeout == null || sendTimeout.isNegative() || sendTimeout.isZero()
                ? DEFAULT_SEND_TIMEOUT
                : sendTimeout;
    }

    /**
     * 发布消息到 Kafka topic，使用 messageId 作为分区 key，并等待 broker 确认。
     *
     * @throws MessagePublishException 发送失败、超时或线程被中断
     */
    @Override
    public void publish(MessageEnvelope<?> message) {
        MessageEnvelope<?> tracedMessage = message.withTraceHeaders();
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                tracedMessage.topic(),
                tracedMessage.messageId(),
                tracedMessage.payload()
        );
        tracedMessage.headers().forEach((name, value) -> addHeader(record, name, value));
        // 与 RocketMQ 适配器保持一致，消费端统一从 message-id 头读取消息 ID。
        addHeader(record, MessageHeaders.MESSAGE_ID, tracedMessage.messageId());
        if (tracedMessage.tag() != null && !tracedMessage.tag().isBlank()) {
            addHeader(record, MessageHeaders.MESSAGE_TAG, tracedMessage.tag());
        }
        try {
            kafkaTemplate.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MessagePublishException("Kafka send interrupted, messageId=" + tracedMessage.messageId(), ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new MessagePublishException("Kafka send failed, messageId=" + tracedMessage.messageId()
                    + ": " + cause.getMessage(), cause);
        } catch (TimeoutException ex) {
            throw new MessagePublishException("Kafka send timeout after " + sendTimeout.toMillis()
                    + "ms, messageId=" + tracedMessage.messageId(), ex);
        } catch (RuntimeException ex) {
            // 序列化失败、元数据获取超时等会在 send 调用时同步抛出。
            throw new MessagePublishException("Kafka send failed, messageId=" + tracedMessage.messageId()
                    + ": " + ex.getMessage(), ex);
        }
    }

    private static void addHeader(ProducerRecord<String, Object> record, String name, String value) {
        if (value == null) {
            return;
        }
        record.headers().remove(name);
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
