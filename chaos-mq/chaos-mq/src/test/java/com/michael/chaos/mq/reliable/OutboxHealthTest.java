package com.michael.chaos.mq.reliable;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * outbox 积压健康判定测试。
 */
class OutboxHealthTest {

    /**
     * 只实现统计的最小仓储；其余方法本测试用不到。
     */
    private static class CountingRepository implements OutboxMessageRepository {

        private final long pending;

        private final long deadLetter;

        private final RuntimeException failure;

        CountingRepository(long pending, long deadLetter, RuntimeException failure) {
            this.pending = pending;
            this.deadLetter = deadLetter;
            this.failure = failure;
        }

        @Override
        public long countByStatus(ReliableMessageStatus status) {
            if (failure != null) {
                throw failure;
            }
            return status == ReliableMessageStatus.PENDING ? pending : deadLetter;
        }

        @Override
        public void save(ReliableMessage message) {
        }

        @Override
        public Optional<ReliableMessage> findByMessageId(String messageId) {
            return Optional.empty();
        }

        @Override
        public List<ReliableMessage> findDueMessages(Instant now, int limit) {
            return List.of();
        }

        @Override
        public List<ReliableMessage> claimDueMessages(Instant now, int limit) {
            return List.of();
        }

        @Override
        public void update(ReliableMessage message) {
        }

        @Override
        public boolean completeClaim(ReliableMessage message) {
            return true;
        }

        @Override
        public int deleteSentMessagesBefore(Instant sentBefore, int limit) {
            return 0;
        }
    }

    /**
     * 积压量在阈值内时健康检查必须是 UP，否则轻微积压就会把实例摘掉。
     */
    @Test
    void shouldBeUpWithinThresholds() {
        OutboxHealth health = new OutboxHealth(new CountingRepository(10, 0, null), 100, 10);

        OutboxHealth.Snapshot snapshot = health.check();

        assertThat(snapshot.status()).isEqualTo(OutboxHealth.Status.UP);
        assertThat(snapshot.details()).containsEntry("pending", 10L).containsEntry("deadLetter", 0L);
    }

    /**
     * 待发送消息堆积超阈值说明投递链路已经跟不上，必须在健康检查里暴露出来。
     */
    @Test
    void shouldBeDownWhenPendingExceedsThreshold() {
        OutboxHealth health = new OutboxHealth(new CountingRepository(101, 0, null), 100, 10);

        assertThat(health.check().status()).isEqualTo(OutboxHealth.Status.DOWN);
    }

    /**
     * 死信堆积意味着消息已经彻底投递失败，比积压更严重，同样要报 DOWN。
     */
    @Test
    void shouldBeDownWhenDeadLetterExceedsThreshold() {
        OutboxHealth health = new OutboxHealth(new CountingRepository(0, 11, null), 100, 10);

        assertThat(health.check().status()).isEqualTo(OutboxHealth.Status.DOWN);
    }

    /**
     * 仓储不支持统计时必须是 UNKNOWN：把"查不到"渲染成"积压为 0"比没有健康检查更危险。
     */
    @Test
    void shouldBeUnknownWhenRepositoryDoesNotSupportCounting() {
        OutboxHealth health = new OutboxHealth(new CountingRepository(-1, -1, null), 100, 10);

        OutboxHealth.Snapshot snapshot = health.check();

        assertThat(snapshot.status()).isEqualTo(OutboxHealth.Status.UNKNOWN);
        assertThat(snapshot.details()).containsKey("reason");
    }

    /**
     * 统计抛异常（数据库不可用）判定为 DOWN，而不是把异常抛给健康端点。
     */
    @Test
    void shouldBeDownWhenCountingFails() {
        OutboxHealth health = new OutboxHealth(
                new CountingRepository(0, 0, new IllegalStateException("db down")), 100, 10);

        OutboxHealth.Snapshot snapshot = health.check();

        assertThat(snapshot.status()).isEqualTo(OutboxHealth.Status.DOWN);
        assertThat(snapshot.details()).containsEntry("error", "IllegalStateException");
    }

    /**
     * 未覆盖 countByStatus 的旧仓储走接口默认实现，返回 -1 而不是 0，
     * 否则它们会被误判为"积压为 0"，健康检查永远 UP。
     */
    @Test
    void repositoryDefaultShouldReportUnsupported() {
        OutboxMessageRepository legacy = new OutboxMessageRepository() {

            @Override
            public void save(ReliableMessage message) {
            }

            @Override
            public Optional<ReliableMessage> findByMessageId(String messageId) {
                return Optional.empty();
            }

            @Override
            public List<ReliableMessage> findDueMessages(Instant now, int limit) {
                return List.of();
            }

            @Override
            public List<ReliableMessage> claimDueMessages(Instant now, int limit) {
                return List.of();
            }

            @Override
            public void update(ReliableMessage message) {
            }

            @Override
            public boolean completeClaim(ReliableMessage message) {
                return true;
            }

            @Override
            public int deleteSentMessagesBefore(Instant sentBefore, int limit) {
                return 0;
            }
        };

        assertThat(legacy.countByStatus(ReliableMessageStatus.PENDING)).isEqualTo(-1L);
        assertThat(new OutboxHealth(legacy, 100, 10).check().status()).isEqualTo(OutboxHealth.Status.UNKNOWN);
    }
}
