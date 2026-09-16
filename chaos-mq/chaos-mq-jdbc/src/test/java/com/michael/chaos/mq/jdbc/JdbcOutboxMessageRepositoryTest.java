package com.michael.chaos.mq.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.mq.reliable.ReliableMessage;
import com.michael.chaos.mq.reliable.ReliableMessageStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * JDBC outbox 仓储测试。
 */
class JdbcOutboxMessageRepositoryTest {

    private EmbeddedDatabase database;

    private JdbcOutboxMessageRepository repository;

    /**
     * 初始化 H2 数据库和 outbox 表。
     */
    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("db/chaos-mq-outbox-schema.sql")).execute(database);
        repository = new JdbcOutboxMessageRepository(new JdbcTemplate(database), new ObjectMapper(), "chaos_mq_outbox");
    }

    /**
     * 保存后应能按消息 ID 查询并还原消息字段。
     */
    @Test
    void shouldSaveAndFindMessage() {
        ReliableMessage message = ReliableMessage.pending(envelope("msg-1"));

        repository.save(message);

        ReliableMessage saved = repository.findByMessageId("msg-1").orElseThrow();
        assertThat(saved.message().messageId()).isEqualTo("msg-1");
        assertThat(saved.message().topic()).isEqualTo("order.created");
        assertThat(saved.message().headers()).containsEntry("X-Trace-Id", "trace-1");
        assertThat(saved.status()).isEqualTo(ReliableMessageStatus.PENDING);
    }

    /**
     * 到期查询只返回待发送或待重试消息。
     */
    @Test
    void shouldFindDueMessagesOnly() {
        repository.save(ReliableMessage.pending(envelope("msg-2")));
        repository.save(ReliableMessage.pending(envelope("msg-3"))
                .retrying(Instant.parse("2026-05-25T01:00:00Z"), "broker unavailable"));

        assertThat(repository.findDueMessages(Instant.parse("2026-05-25T00:00:00Z"), 10))
                .extracting(message -> message.message().messageId())
                .containsExactly("msg-2");
        assertThat(repository.findDueMessages(Instant.parse("2026-05-25T02:00:00Z"), 10))
                .extracting(message -> message.message().messageId())
                .containsExactly("msg-2", "msg-3");
    }

    /**
     * 更新状态后应持久化重试次数、下一次重试时间和错误信息。
     */
    @Test
    void shouldUpdateMessageStatus() {
        repository.save(ReliableMessage.pending(envelope("msg-4")));
        ReliableMessage retrying = repository.findByMessageId("msg-4").orElseThrow()
                .retrying(Instant.parse("2026-05-25T01:00:00Z"), "broker unavailable");

        repository.update(retrying);

        ReliableMessage saved = repository.findByMessageId("msg-4").orElseThrow();
        assertThat(saved.status()).isEqualTo(ReliableMessageStatus.RETRYING);
        assertThat(saved.retryTimes()).isEqualTo(1);
        assertThat(saved.nextRetryAt()).isEqualTo(Instant.parse("2026-05-25T01:00:00Z"));
        assertThat(saved.lastError()).isEqualTo("broker unavailable");
    }

    /**
     * 已被一个仓储实例抢占的消息，另一个实例不应再次抢占。
     */
    @Test
    void shouldClaimDueMessagesOnlyOnceAcrossInstances() {
        repository.save(ReliableMessage.pending(envelope("msg-5")));
        JdbcOutboxMessageRepository anotherRepository =
                new JdbcOutboxMessageRepository(new JdbcTemplate(database), new ObjectMapper(), "chaos_mq_outbox");

        assertThat(repository.claimDueMessages(Instant.parse("2026-05-25T00:00:00Z"), 10))
                .extracting(message -> message.message().messageId())
                .containsExactly("msg-5");
        assertThat(anotherRepository.claimDueMessages(Instant.parse("2026-05-25T00:00:00Z"), 10))
                .isEmpty();
        assertThat(repository.findByMessageId("msg-5")).get()
                .extracting(ReliableMessage::status)
                .isEqualTo(ReliableMessageStatus.SENDING);
    }

    /**
     * 发送中超过 claim 超时时间的消息应允许重新抢占。
     */
    @Test
    void shouldReclaimSendingMessagesAfterClaimTimeout() {
        repository.save(ReliableMessage.pending(envelope("msg-6")));
        Instant firstClaimAt = Instant.parse("2026-05-25T00:00:00Z");
        repository.claimDueMessages(firstClaimAt, 10);
        JdbcOutboxMessageRepository anotherRepository =
                new JdbcOutboxMessageRepository(new JdbcTemplate(database), new ObjectMapper(), "chaos_mq_outbox");

        assertThat(anotherRepository.claimDueMessages(
                Instant.parse("2026-05-25T00:01:00Z"),
                10,
                Instant.parse("2026-05-24T23:59:59Z")
        )).isEmpty();

        assertThat(anotherRepository.claimDueMessages(
                Instant.parse("2026-05-25T00:06:00Z"),
                10,
                Instant.parse("2026-05-25T00:01:00Z")
        )).extracting(message -> message.message().messageId())
                .containsExactly("msg-6");
    }

    /**
     * 回收发送超时消息时应累加重试次数。
     */
    @Test
    void shouldIncreaseRetryTimesWhenReclaimingSendingMessage() {
        repository.save(ReliableMessage.pending(envelope("msg-7")));
        repository.claimDueMessages(Instant.parse("2026-05-25T00:00:00Z"), 10);
        JdbcOutboxMessageRepository anotherRepository =
                new JdbcOutboxMessageRepository(new JdbcTemplate(database), new ObjectMapper(), "chaos_mq_outbox");

        assertThat(anotherRepository.claimDueMessages(
                Instant.parse("2026-05-25T00:06:00Z"), 10, Instant.parse("2026-05-25T00:01:00Z")))
                .singleElement()
                .extracting(ReliableMessage::retryTimes)
                .isEqualTo(1);
        assertThat(repository.findByMessageId("msg-7")).get()
                .extracting(ReliableMessage::retryTimes)
                .isEqualTo(1);
    }

    /**
     * claim 被其他实例接管后，原实例迟到的结果不能覆盖状态。
     */
    @Test
    void shouldRejectCompletionFromInstanceThatLostClaim() {
        repository.save(ReliableMessage.pending(envelope("msg-8")));
        ReliableMessage firstClaim = repository.claimDueMessages(Instant.parse("2026-05-25T00:00:00Z"), 10).getFirst();
        JdbcOutboxMessageRepository anotherRepository =
                new JdbcOutboxMessageRepository(new JdbcTemplate(database), new ObjectMapper(), "chaos_mq_outbox");
        ReliableMessage secondClaim = anotherRepository.claimDueMessages(
                Instant.parse("2026-05-25T00:06:00Z"), 10, Instant.parse("2026-05-25T00:01:00Z")).getFirst();

        assertThat(anotherRepository.completeClaim(secondClaim.sent())).isTrue();
        assertThat(repository.completeClaim(firstClaim.retrying(Instant.parse("2026-05-25T01:00:00Z"), "late failure")))
                .isFalse();
        assertThat(repository.findByMessageId("msg-8")).get()
                .extracting(ReliableMessage::status)
                .isEqualTo(ReliableMessageStatus.SENT);
    }

    /**
     * 超长错误信息应能写入 varchar(1024) 字段。
     */
    @Test
    void shouldPersistTruncatedLastError() {
        repository.save(ReliableMessage.pending(envelope("msg-9")));
        ReliableMessage claimed = repository.claimDueMessages(Instant.parse("2026-05-25T00:00:00Z"), 10).getFirst();

        assertThat(repository.completeClaim(claimed.retrying(Instant.parse("2026-05-25T01:00:00Z"), "e".repeat(4000))))
                .isTrue();
        assertThat(repository.findByMessageId("msg-9").orElseThrow().lastError())
                .hasSize(ReliableMessage.MAX_LAST_ERROR_LENGTH);
    }

    /**
     * 只清理保留期之前的 SENT 消息。
     */
    @Test
    void shouldDeleteOnlyExpiredSentMessages() {
        repository.save(ReliableMessage.pending(envelope("msg-10")));
        repository.save(ReliableMessage.pending(envelope("msg-11")));
        ReliableMessage claimed = repository.claimDueMessages(Instant.parse("2026-05-25T00:00:00Z"), 1).getFirst();
        repository.completeClaim(claimed.sent());

        assertThat(repository.deleteSentMessagesBefore(Instant.now().minusSeconds(3600), 100)).isZero();
        assertThat(repository.deleteSentMessagesBefore(Instant.now().plusSeconds(60), 100)).isEqualTo(1);
        assertThat(repository.findByMessageId(claimed.message().messageId())).isEmpty();
        assertThat(repository.findByMessageId("msg-10").isPresent() || repository.findByMessageId("msg-11").isPresent())
                .isTrue();
    }

    /**
     * 查询不存在的消息返回空，基础设施异常必须抛出。
     */
    @Test
    void shouldReturnEmptyOnlyWhenMessageMissing() {
        assertThat(repository.findByMessageId("missing")).isEmpty();
        database.shutdown();
        assertThatThrownBy(() -> repository.findByMessageId("missing"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    /**
     * 非法表名应在构造时拒绝。
     */
    @Test
    void shouldRejectIllegalTableName() {
        assertThatThrownBy(() -> new JdbcOutboxMessageRepository(
                new JdbcTemplate(database), new ObjectMapper(), "chaos_mq_outbox; drop table x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MessageEnvelope<Map<String, Object>> envelope(String messageId) {
        return new MessageEnvelope<>(
                messageId,
                "order.created",
                "created",
                Map.of("orderNo", messageId, "amount", new BigDecimal("199.90")),
                Map.of("X-Trace-Id", "trace-1"),
                Instant.parse("2026-05-25T00:00:00Z")
        );
    }
}
