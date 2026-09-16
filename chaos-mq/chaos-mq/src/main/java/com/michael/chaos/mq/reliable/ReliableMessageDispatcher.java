package com.michael.chaos.mq.reliable;

import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.metrics.NoopChaosMetrics;
import com.michael.chaos.mq.MessagePublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可靠消息 outbox 派发器。
 *
 * <p>语义为 at-least-once：发布成功但状态更新失败时，消息会在 claim 超时后被重新发送，消费端必须幂等。</p>
 *
 * <p>派发流程：</p>
 * <ol>
 *     <li>通过仓储原子抢占到期消息（含发送超时的 SENDING 消息）；</li>
 *     <li>逐条发送，单条异常不会影响同批其他消息；</li>
 *     <li>用 {@link OutboxMessageRepository#completeClaim} 按 claim owner 回写结果，防止迟到结果覆盖其他实例的状态。</li>
 * </ol>
 */
public class ReliableMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReliableMessageDispatcher.class);

    private final OutboxMessageRepository repository;

    private final MessagePublisher messagePublisher;

    private final DeadLetterMessageHandler deadLetterMessageHandler;

    private final RetryBackoffStrategy retryBackoffStrategy;

    private final Clock clock;

    private final int maxRetryTimes;

    private final Duration claimTimeout;

    private final ChaosMetrics metrics;

    /**
     * 创建可靠消息派发器。
     */
    public ReliableMessageDispatcher(
            OutboxMessageRepository repository,
            MessagePublisher messagePublisher,
            DeadLetterMessageHandler deadLetterMessageHandler,
            RetryBackoffStrategy retryBackoffStrategy,
            Clock clock,
            int maxRetryTimes) {
        this(repository, messagePublisher, deadLetterMessageHandler, retryBackoffStrategy, clock, maxRetryTimes,
                Duration.ofMinutes(5));
    }

    /**
     * 创建支持发送中超时恢复的可靠消息派发器。
     */
    public ReliableMessageDispatcher(
            OutboxMessageRepository repository,
            MessagePublisher messagePublisher,
            DeadLetterMessageHandler deadLetterMessageHandler,
            RetryBackoffStrategy retryBackoffStrategy,
            Clock clock,
            int maxRetryTimes,
            Duration claimTimeout) {
        this(repository, messagePublisher, deadLetterMessageHandler, retryBackoffStrategy, clock, maxRetryTimes,
                claimTimeout, null);
    }

    /**
     * 创建可靠消息派发器并上报治理指标。
     *
     * @param metrics 治理指标上报端口，可为 {@code null}
     */
    public ReliableMessageDispatcher(
            OutboxMessageRepository repository,
            MessagePublisher messagePublisher,
            DeadLetterMessageHandler deadLetterMessageHandler,
            RetryBackoffStrategy retryBackoffStrategy,
            Clock clock,
            int maxRetryTimes,
            Duration claimTimeout,
            ChaosMetrics metrics) {
        this.repository = repository;
        this.messagePublisher = messagePublisher;
        this.deadLetterMessageHandler = deadLetterMessageHandler == null
                ? new NoopDeadLetterMessageHandler()
                : deadLetterMessageHandler;
        this.retryBackoffStrategy = retryBackoffStrategy == null
                ? new FixedRetryBackoffStrategy(Duration.ofSeconds(5))
                : retryBackoffStrategy;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.maxRetryTimes = Math.max(maxRetryTimes, 0);
        this.claimTimeout = claimTimeout == null || claimTimeout.isNegative() || claimTimeout.isZero()
                ? Duration.ofMinutes(5)
                : claimTimeout;
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
    }

    /**
     * 派发一批到期消息。
     *
     * @param batchSize 批次大小
     * @return 实际抢占并处理的条数
     */
    public int dispatchDueMessages(int batchSize) {
        Instant now = Instant.now(clock);
        List<ReliableMessage> messages = repository.claimDueMessages(
                now,
                Math.max(batchSize, 1),
                now.minus(claimTimeout)
        );
        for (ReliableMessage message : messages) {
            try {
                dispatchOne(message);
            } catch (RuntimeException ex) {
                // 单条隔离：状态回写或死信处理失败时只记录日志，claim 超时后该消息会被重新回收。
                log.error("Outbox message dispatch failed unexpectedly, messageId={}", messageId(message), ex);
            }
        }
        return messages.size();
    }

    /**
     * 删除保留期之前已发送的消息。
     *
     * @param retention 已发送消息保留时长
     * @param batchSize 单批删除条数
     * @return 删除条数
     */
    public int purgeSentMessages(Duration retention, int batchSize) {
        Duration effectiveRetention = retention == null || retention.isNegative() ? Duration.ZERO : retention;
        return repository.deleteSentMessagesBefore(Instant.now(clock).minus(effectiveRetention), Math.max(batchSize, 1));
    }

    private void dispatchOne(ReliableMessage message) {
        if (message.retryTimes() > maxRetryTimes) {
            // 发送超时回收同样累加重试次数，超过上限直接进入死信，避免“每次都卡住”的消息被无限回收。
            deadLetter(message, message.lastError().isBlank() ? "max retry times exceeded" : message.lastError());
            return;
        }
        try {
            messagePublisher.publish(message.message());
        } catch (RuntimeException ex) {
            handleFailure(message, ex);
            return;
        }
        metrics.increment(ChaosMeterNames.MQ_OUTBOX_DISPATCHED, ChaosMeterNames.TAG_OUTCOME, "sent");
        try {
            if (!repository.completeClaim(message.sent())) {
                log.warn("Outbox message published but claim was taken over by another dispatcher, messageId={}",
                        messageId(message));
            }
        } catch (RuntimeException ex) {
            log.warn("Message published but status update failed, messageId={}: {}", messageId(message), ex.getMessage());
        }
    }

    private void handleFailure(ReliableMessage message, RuntimeException ex) {
        String error = ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
        if (message.retryTimes() >= maxRetryTimes) {
            deadLetter(message, error);
            return;
        }
        metrics.increment(ChaosMeterNames.MQ_OUTBOX_DISPATCHED, ChaosMeterNames.TAG_OUTCOME, "retry");
        Duration backoff = retryBackoffStrategy.nextBackoff(message.retryTimes() + 1);
        if (!repository.completeClaim(message.retrying(Instant.now(clock).plus(backoff), error))) {
            log.warn("Outbox message failure ignored because claim was taken over, messageId={}", messageId(message));
        }
    }

    private void deadLetter(ReliableMessage message, String error) {
        metrics.increment(ChaosMeterNames.MQ_OUTBOX_DISPATCHED, ChaosMeterNames.TAG_OUTCOME, "dead");
        ReliableMessage deadLetter = message.deadLetter(error);
        if (repository.completeClaim(deadLetter)) {
            deadLetterMessageHandler.handle(deadLetter);
        } else {
            log.warn("Outbox dead letter ignored because claim was taken over, messageId={}", messageId(message));
        }
    }

    private static String messageId(ReliableMessage message) {
        return message.message().messageId();
    }
}
