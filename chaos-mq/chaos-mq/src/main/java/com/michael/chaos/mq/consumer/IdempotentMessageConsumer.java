package com.michael.chaos.mq.consumer;

import com.michael.chaos.core.idempotent.IdempotentRepository;
import com.michael.chaos.mq.MessageEnvelope;
import java.time.Duration;
import java.util.Objects;

/**
 * 带幂等保护的消息消费者包装器。
 *
 * <p>采用 PROCESSING / DONE 两段式标记，替代旧版“先占位 1 天再处理”：</p>
 * <ul>
 *     <li>PROCESSING 标记 TTL 很短（默认 5 分钟）。进程在处理中崩溃时，标记很快过期，MQ 重投后可以重新处理，消息不会丢；</li>
 *     <li>DONE 标记只在业务成功后写入，保留幂等窗口（默认 1 天）；</li>
 *     <li>并发重复投递时抛出 {@link MessageRetryLaterException}，让 MQ 稍后重投，而不是直接 ack。</li>
 * </ul>
 *
 * <p>{@link IdempotentRepository} 只有 saveIfAbsent/remove 两个原语，没有 exists。检查 DONE 时用
 * “saveIfAbsent 后立即 remove”的方式探测；该操作在持有 PROCESSING 标记期间执行，不会与其他消费者竞争。
 * 剩余风险是探测写入后、remove 前进程崩溃，这条消息在幂等窗口内会被视为已完成。</p>
 *
 * @param <T> 消息体类型
 */
public class IdempotentMessageConsumer<T> implements MessageConsumer<T> {

    private static final Duration DEFAULT_TTL = Duration.ofDays(1);

    private static final Duration DEFAULT_PROCESSING_TTL = Duration.ofMinutes(5);

    private final MessageConsumer<T> delegate;

    private final IdempotentRepository repository;

    private final Duration ttl;

    private final Duration processingTtl;

    private final String consumerGroup;

    /**
     * 创建默认 1 天幂等窗口的消息消费者。
     */
    public IdempotentMessageConsumer(MessageConsumer<T> delegate, IdempotentRepository repository) {
        this(delegate, repository, DEFAULT_TTL);
    }

    /**
     * 创建指定幂等窗口的消息消费者。
     */
    public IdempotentMessageConsumer(MessageConsumer<T> delegate, IdempotentRepository repository, Duration ttl) {
        this(delegate, repository, ttl, DEFAULT_PROCESSING_TTL, "");
    }

    /**
     * 创建指定幂等窗口、处理中超时和消费组的消息消费者。
     *
     * @param delegate 业务消费者
     * @param repository 幂等存储
     * @param ttl 完成标记保留时长
     * @param processingTtl 处理中标记保留时长，应大于单条消息最大处理耗时
     * @param consumerGroup 消费组；多个服务共用同一个 Redis 消费同一 topic 时必须区分，否则 A 服务消费后 B 服务会跳过
     */
    public IdempotentMessageConsumer(
            MessageConsumer<T> delegate,
            IdempotentRepository repository,
            Duration ttl,
            Duration processingTtl,
            String consumerGroup) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.ttl = positiveOrDefault(ttl, DEFAULT_TTL);
        this.processingTtl = positiveOrDefault(processingTtl, DEFAULT_PROCESSING_TTL);
        this.consumerGroup = consumerGroup == null ? "" : consumerGroup.trim();
    }

    /**
     * 同一个 consumerGroup + topic + messageId 在幂等窗口内只成功消费一次。
     */
    @Override
    public void consume(MessageEnvelope<T> message) {
        String baseKey = baseKey(message);
        String processingKey = baseKey + ":processing";
        String doneKey = baseKey + ":done";
        if (!repository.saveIfAbsent(processingKey, processingTtl)) {
            throw new MessageRetryLaterException("message is being processed by another consumer: " + message.messageId());
        }
        try {
            if (isDone(doneKey)) {
                return;
            }
            delegate.consume(message);
            repository.saveIfAbsent(doneKey, ttl);
        } finally {
            repository.remove(processingKey);
        }
    }

    private boolean isDone(String doneKey) {
        if (!repository.saveIfAbsent(doneKey, processingTtl)) {
            return true;
        }
        repository.remove(doneKey);
        return false;
    }

    private String baseKey(MessageEnvelope<T> message) {
        String group = consumerGroup.isEmpty() ? "" : consumerGroup + ":";
        return "mq:" + group + message.topic() + ":" + message.messageId();
    }

    private static Duration positiveOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isNegative() || value.isZero() ? defaultValue : value;
    }
}
