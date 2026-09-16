package com.michael.chaos.mq.reliable;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.mq.MessageEnvelope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 派发器单条隔离、claim owner 校验、错误截断和清理测试。
 */
class ReliableMessageDispatcherIsolationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-24T01:00:00Z"), ZoneOffset.UTC);

    /**
     * 单条消息回写失败时，同批次其他消息仍应继续派发。
     */
    @Test
    void shouldIsolateFailureOfSingleMessage() {
        RecordingRepository repository = new RecordingRepository();
        repository.claimable.add(pendingSending("msg-1"));
        repository.claimable.add(pendingSending("msg-2"));
        repository.failCompleteFor = "msg-1";
        List<String> published = new ArrayList<>();
        ReliableMessageDispatcher dispatcher = dispatcher(repository, message -> {
            published.add(message.messageId());
            throw new IllegalStateException("broker down");
        }, 3);

        int count = dispatcher.dispatchDueMessages(10);

        assertThat(count).isEqualTo(2);
        assertThat(published).containsExactly("msg-1", "msg-2");
        assertThat(repository.completed).extracting(message -> message.message().messageId()).containsExactly("msg-2");
    }

    /**
     * claim 已被其他实例接管时不应触发死信处理。
     */
    @Test
    void shouldNotHandleDeadLetterWhenClaimLost() {
        RecordingRepository repository = new RecordingRepository();
        repository.claimable.add(new ReliableMessage(envelope("msg-3"), ReliableMessageStatus.SENDING, 3,
                CLOCK.instant(), "", CLOCK.instant(), CLOCK.instant()));
        repository.claimLost = true;
        List<ReliableMessage> deadLetters = new ArrayList<>();
        ReliableMessageDispatcher dispatcher = new ReliableMessageDispatcher(
                repository,
                message -> {
                    throw new IllegalStateException("broker down");
                },
                deadLetters::add,
                new FixedRetryBackoffStrategy(Duration.ofSeconds(5)),
                CLOCK,
                3);

        dispatcher.dispatchDueMessages(10);

        assertThat(deadLetters).isEmpty();
    }

    /**
     * 回收次数超过上限的消息不再发送，直接进入死信。
     */
    @Test
    void shouldDeadLetterReclaimedMessageExceedingMaxRetryTimes() {
        RecordingRepository repository = new RecordingRepository();
        repository.claimable.add(new ReliableMessage(envelope("msg-4"), ReliableMessageStatus.SENDING, 4,
                CLOCK.instant(), "claim timeout, reclaimed", CLOCK.instant(), CLOCK.instant()));
        List<String> published = new ArrayList<>();
        ReliableMessageDispatcher dispatcher = dispatcher(repository, message -> published.add(message.messageId()), 3);

        dispatcher.dispatchDueMessages(10);

        assertThat(published).isEmpty();
        assertThat(repository.completed).singleElement()
                .extracting(ReliableMessage::status)
                .isEqualTo(ReliableMessageStatus.DEAD_LETTER);
    }

    /**
     * 超长错误信息应截断到表字段长度。
     */
    @Test
    void shouldTruncateLongErrorMessage() {
        RecordingRepository repository = new RecordingRepository();
        repository.claimable.add(pendingSending("msg-5"));
        ReliableMessageDispatcher dispatcher = dispatcher(repository, message -> {
            throw new IllegalStateException("x".repeat(5000));
        }, 3);

        dispatcher.dispatchDueMessages(10);

        assertThat(repository.completed).singleElement()
                .satisfies(message -> {
                    assertThat(message.status()).isEqualTo(ReliableMessageStatus.RETRYING);
                    assertThat(message.lastError()).hasSize(ReliableMessage.MAX_LAST_ERROR_LENGTH);
                });
    }

    /**
     * 清理应按保留期计算截止时间。
     */
    @Test
    void shouldPurgeSentMessagesBeforeRetention() {
        RecordingRepository repository = new RecordingRepository();
        ReliableMessageDispatcher dispatcher = dispatcher(repository, message -> {
        }, 3);

        dispatcher.purgeSentMessages(Duration.ofDays(7), 200);

        assertThat(repository.purgeBefore).isEqualTo(Instant.parse("2026-05-17T01:00:00Z"));
        assertThat(repository.purgeLimit).isEqualTo(200);
    }

    private static ReliableMessageDispatcher dispatcher(
            RecordingRepository repository,
            com.michael.chaos.mq.MessagePublisher publisher,
            int maxRetryTimes) {
        return new ReliableMessageDispatcher(
                repository,
                publisher,
                message -> {
                },
                new FixedRetryBackoffStrategy(Duration.ofSeconds(5)),
                CLOCK,
                maxRetryTimes);
    }

    private static ReliableMessage pendingSending(String messageId) {
        return ReliableMessage.pending(envelope(messageId)).sending(CLOCK.instant());
    }

    private static MessageEnvelope<String> envelope(String messageId) {
        return new MessageEnvelope<>(messageId, "order.created", "", "payload", Map.of(), CLOCK.instant());
    }

    private static final class RecordingRepository implements OutboxMessageRepository {

        private final List<ReliableMessage> claimable = new ArrayList<>();

        private final List<ReliableMessage> completed = new ArrayList<>();

        private final Map<String, ReliableMessage> saved = new LinkedHashMap<>();

        private String failCompleteFor;

        private boolean claimLost;

        private Instant purgeBefore;

        private int purgeLimit;

        @Override
        public void save(ReliableMessage message) {
            saved.put(message.message().messageId(), message);
        }

        @Override
        public Optional<ReliableMessage> findByMessageId(String messageId) {
            return Optional.ofNullable(saved.get(messageId));
        }

        @Override
        public List<ReliableMessage> findDueMessages(Instant now, int limit) {
            return List.of();
        }

        @Override
        public List<ReliableMessage> claimDueMessages(Instant now, int limit) {
            return List.copyOf(claimable);
        }

        @Override
        public void update(ReliableMessage message) {
            saved.put(message.message().messageId(), message);
        }

        @Override
        public boolean completeClaim(ReliableMessage message) {
            if (message.message().messageId().equals(failCompleteFor)) {
                throw new IllegalStateException("db unavailable");
            }
            if (claimLost) {
                return false;
            }
            completed.add(message);
            return true;
        }

        @Override
        public int deleteSentMessagesBefore(Instant sentBefore, int limit) {
            purgeBefore = sentBefore;
            purgeLimit = limit;
            return 0;
        }
    }
}
