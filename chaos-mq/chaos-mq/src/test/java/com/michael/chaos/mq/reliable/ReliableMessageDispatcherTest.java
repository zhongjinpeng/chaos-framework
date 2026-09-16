package com.michael.chaos.mq.reliable;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.mq.MessagePublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 可靠消息派发器测试。
 */
class ReliableMessageDispatcherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-24T01:00:00Z"), ZoneOffset.UTC);

    /**
     * 发送成功后应把消息标记为 SENT。
     */
    @Test
    void shouldMarkMessageSentWhenPublishSuccess() {
        InMemoryOutboxRepository repository = new InMemoryOutboxRepository();
        MessageEnvelope<String> envelope = envelope("msg-1");
        repository.save(ReliableMessage.pending(envelope));
        ReliableMessageDispatcher dispatcher = new ReliableMessageDispatcher(
                repository,
                message -> {
                },
                message -> {
                },
                new FixedRetryBackoffStrategy(Duration.ofSeconds(5)),
                CLOCK,
                3
        );

        int count = dispatcher.dispatchDueMessages(10);

        assertThat(count).isEqualTo(1);
        assertThat(repository.findByMessageId("msg-1")).get()
                .extracting(ReliableMessage::status)
                .isEqualTo(ReliableMessageStatus.SENT);
    }

    /**
     * 发送失败且未超过重试上限时应进入 RETRYING。
     */
    @Test
    void shouldRetryWhenPublishFailsBeforeMaxRetryTimes() {
        InMemoryOutboxRepository repository = new InMemoryOutboxRepository();
        repository.save(ReliableMessage.pending(envelope("msg-2")));
        ReliableMessageDispatcher dispatcher = new ReliableMessageDispatcher(
                repository,
                failingPublisher(),
                message -> {
                },
                new FixedRetryBackoffStrategy(Duration.ofSeconds(30)),
                CLOCK,
                3
        );

        dispatcher.dispatchDueMessages(10);

        ReliableMessage message = repository.findByMessageId("msg-2").orElseThrow();
        assertThat(message.status()).isEqualTo(ReliableMessageStatus.RETRYING);
        assertThat(message.retryTimes()).isEqualTo(1);
        assertThat(message.nextRetryAt()).isEqualTo(Instant.parse("2026-05-24T01:00:30Z"));
    }

    /**
     * 超过重试上限后应进入死信处理。
     */
    @Test
    void shouldSendToDeadLetterWhenRetryTimesExceeded() {
        InMemoryOutboxRepository repository = new InMemoryOutboxRepository();
        ReliableMessage retrying = new ReliableMessage(
                envelope("msg-3"),
                ReliableMessageStatus.RETRYING,
                3,
                Instant.parse("2026-05-24T01:00:00Z"),
                "",
                Instant.parse("2026-05-24T00:00:00Z"),
                Instant.parse("2026-05-24T00:00:00Z")
        );
        repository.save(retrying);
        List<ReliableMessage> deadLetters = new ArrayList<>();
        ReliableMessageDispatcher dispatcher = new ReliableMessageDispatcher(
                repository,
                failingPublisher(),
                deadLetters::add,
                new FixedRetryBackoffStrategy(Duration.ofSeconds(30)),
                CLOCK,
                3
        );

        dispatcher.dispatchDueMessages(10);

        ReliableMessage message = repository.findByMessageId("msg-3").orElseThrow();
        assertThat(message.status()).isEqualTo(ReliableMessageStatus.DEAD_LETTER);
        assertThat(deadLetters).hasSize(1);
    }

    /**
     * 派发器必须通过 claim 获取消息，避免多实例重复派发。
     */
    @Test
    void shouldDispatchClaimedMessages() {
        ClaimOnlyOutboxRepository repository = new ClaimOnlyOutboxRepository();
        repository.save(ReliableMessage.pending(envelope("msg-4")));
        List<String> published = new ArrayList<>();
        ReliableMessageDispatcher dispatcher = new ReliableMessageDispatcher(
                repository,
                message -> published.add(message.messageId()),
                message -> {
                },
                new FixedRetryBackoffStrategy(Duration.ofSeconds(5)),
                CLOCK,
                3
        );

        int count = dispatcher.dispatchDueMessages(10);

        assertThat(count).isEqualTo(1);
        assertThat(repository.claimCalled).isTrue();
        assertThat(published).containsExactly("msg-4");
    }

    /**
     * 派发器应把 claim 超时边界传给仓储，用于回收 SENDING 消息。
     */
    @Test
    void shouldPassClaimTimeoutBoundaryToRepository() {
        ClaimTimeoutAwareOutboxRepository repository = new ClaimTimeoutAwareOutboxRepository();
        ReliableMessageDispatcher dispatcher = new ReliableMessageDispatcher(
                repository,
                message -> {
                },
                message -> {
                },
                new FixedRetryBackoffStrategy(Duration.ofSeconds(5)),
                CLOCK,
                3,
                Duration.ofMinutes(2)
        );

        dispatcher.dispatchDueMessages(10);

        assertThat(repository.claimTimeoutAt).isEqualTo(Instant.parse("2026-05-24T00:58:00Z"));
    }

    private static MessagePublisher failingPublisher() {
        return message -> {
            throw new IllegalStateException("broker unavailable");
        };
    }

    private static MessageEnvelope<String> envelope(String messageId) {
        return new MessageEnvelope<>(messageId, "order.created", "created", "payload", Map.of(), CLOCK.instant());
    }

    private static class InMemoryOutboxRepository implements OutboxMessageRepository {

        private final List<ReliableMessage> messages = new ArrayList<>();

        @Override
        public void save(ReliableMessage message) {
            messages.add(message);
        }

        @Override
        public Optional<ReliableMessage> findByMessageId(String messageId) {
            return messages.stream()
                    .filter(message -> message.message().messageId().equals(messageId))
                    .findFirst();
        }

        @Override
        public List<ReliableMessage> findDueMessages(Instant now, int limit) {
            return messages.stream()
                    .filter(message -> message.status() == ReliableMessageStatus.PENDING
                            || message.status() == ReliableMessageStatus.RETRYING)
                    .filter(message -> message.nextRetryAt() == null || !message.nextRetryAt().isAfter(now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<ReliableMessage> claimDueMessages(Instant now, int limit) {
            List<ReliableMessage> due = findDueMessages(now, limit);
            for (ReliableMessage message : due) {
                update(message.sending(now));
            }
            return due.stream().map(m -> m.sending(now)).toList();
        }

        @Override
        public void update(ReliableMessage message) {
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).message().messageId().equals(message.message().messageId())) {
                    messages.set(i, message);
                    return;
                }
            }
            messages.add(message);
        }
    }

    private static final class ClaimOnlyOutboxRepository extends InMemoryOutboxRepository {

        private boolean claimCalled;

        @Override
        public List<ReliableMessage> findDueMessages(Instant now, int limit) {
            return List.of();
        }

        @Override
        public List<ReliableMessage> claimDueMessages(Instant now, int limit) {
            claimCalled = true;
            return super.findDueMessages(now, limit).stream()
                    .map(message -> message.sending(now))
                    .toList();
        }
    }

    private static final class ClaimTimeoutAwareOutboxRepository extends InMemoryOutboxRepository {

        private Instant claimTimeoutAt;

        @Override
        public List<ReliableMessage> claimDueMessages(Instant now, int limit, Instant claimTimeoutAt) {
            this.claimTimeoutAt = claimTimeoutAt;
            return List.of();
        }
    }
}
