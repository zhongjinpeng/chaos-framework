package com.michael.chaos.mq.rocketmq;

import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.mq.MessageHeaders;
import com.michael.chaos.mq.MessagePublishException;
import com.michael.chaos.mq.MessagePublisher;
import java.time.Duration;
import java.util.Objects;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 基于 RocketMQTemplate 的消息发布器。
 *
 * <p>使用 {@code syncSend} 并校验 {@link SendStatus#SEND_OK}。旧实现调用 {@code send} 不检查结果；
 * 刷盘超时、从节点不可用等非 OK 状态也按失败处理，由 outbox 重试（消费端幂等保证重复无害）。</p>
 */
public class RocketMqMessagePublisher implements MessagePublisher {

    /**
     * 默认发送确认超时时间。
     */
    public static final Duration DEFAULT_SEND_TIMEOUT = Duration.ofSeconds(10);

    private final RocketMQTemplate rocketMQTemplate;

    private final Duration sendTimeout;

    /**
     * 创建默认 10 秒确认超时的 RocketMQ 消息发布器。
     */
    public RocketMqMessagePublisher(RocketMQTemplate rocketMQTemplate) {
        this(rocketMQTemplate, DEFAULT_SEND_TIMEOUT);
    }

    /**
     * 创建指定确认超时的 RocketMQ 消息发布器。
     */
    public RocketMqMessagePublisher(RocketMQTemplate rocketMQTemplate, Duration sendTimeout) {
        this.rocketMQTemplate = Objects.requireNonNull(rocketMQTemplate, "rocketMQTemplate must not be null");
        this.sendTimeout = sendTimeout == null || sendTimeout.isNegative() || sendTimeout.isZero()
                ? DEFAULT_SEND_TIMEOUT
                : sendTimeout;
    }

    /**
     * 同步发布消息到 RocketMQ topic 或 topic:tag。
     *
     * @throws MessagePublishException 发送异常或 broker 返回非 SEND_OK
     */
    @Override
    public void publish(MessageEnvelope<?> message) {
        MessageEnvelope<?> tracedMessage = message.withTraceHeaders();
        String destination = tracedMessage.tag() == null || tracedMessage.tag().isBlank()
                ? tracedMessage.topic()
                : tracedMessage.topic() + ":" + tracedMessage.tag();
        MessageBuilder<Object> builder = MessageBuilder.withPayload(tracedMessage.payload());
        tracedMessage.headers().forEach(builder::setHeader);
        builder.setHeader(MessageHeaders.MESSAGE_ID, tracedMessage.messageId());
        SendResult result;
        try {
            result = rocketMQTemplate.syncSend(destination, builder.build(), sendTimeout.toMillis());
        } catch (RuntimeException ex) {
            throw new MessagePublishException("RocketMQ send failed, messageId=" + tracedMessage.messageId()
                    + ": " + ex.getMessage(), ex);
        }
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new MessagePublishException("RocketMQ send not confirmed, messageId=" + tracedMessage.messageId()
                    + ", status=" + (result == null ? "null" : result.getSendStatus()));
        }
    }
}
