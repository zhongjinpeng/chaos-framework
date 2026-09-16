package com.michael.chaos.mq.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.mq.reliable.OutboxMessageRepository;
import com.michael.chaos.mq.reliable.ReliableMessage;
import com.michael.chaos.mq.reliable.ReliableMessageStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

/**
 * 基于 JDBC 的 outbox 消息仓储。
 *
 * <p>该实现只负责可靠消息持久化，不直接依赖具体 MQ SDK。业务事务内保存 outbox 后，
 * 由 `ReliableMessageDispatcher` 异步读取并调用真实 `MessagePublisher`。</p>
 *
 * <p>多实例安全依赖两点：抢占使用带状态条件的 UPDATE（只有一个实例能更新成功）；回写结果使用
 * {@link #completeClaim}，要求 {@code claim_owner} 仍是当前实例。</p>
 */
public class JdbcOutboxMessageRepository implements OutboxMessageRepository {

    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {
    };

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    private final JdbcOperations jdbcOperations;

    private final ObjectMapper objectMapper;

    private final String tableName;

    private final String claimOwner;

    /**
     * 创建 JDBC outbox 仓储。
     *
     * @param jdbcOperations JDBC 操作入口
     * @param objectMapper JSON 序列化入口
     * @param tableName outbox 表名
     */
    public JdbcOutboxMessageRepository(JdbcOperations jdbcOperations, ObjectMapper objectMapper, String tableName) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "jdbcOperations must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.tableName = safeTableName(tableName);
        this.claimOwner = "chaos-" + UUID.randomUUID();
    }

    /**
     * 插入 outbox 消息。
     */
    @Override
    public void save(ReliableMessage message) {
        jdbcOperations.update("""
                        insert into %s (
                            message_id, topic, tag, payload_json, headers_json, status, retry_times,
                            next_retry_at, last_error, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.formatted(tableName),
                message.message().messageId(),
                message.message().topic(),
                message.message().tag(),
                writeJson(message.message().payload()),
                writeJson(message.message().headers()),
                message.status().name(),
                message.retryTimes(),
                timestamp(message.nextRetryAt()),
                message.lastError(),
                timestamp(message.createdAt()),
                timestamp(message.updatedAt()));
    }

    /**
     * 根据消息 ID 查询 outbox 消息。
     */
    @Override
    public Optional<ReliableMessage> findByMessageId(String messageId) {
        try {
            ReliableMessage message = jdbcOperations.queryForObject("""
                            select message_id, topic, tag, payload_json, headers_json, status, retry_times,
                                   next_retry_at, last_error, created_at, updated_at
                              from %s
                             where message_id = ?
                            """.formatted(tableName),
                    mapper(),
                    messageId);
            return Optional.ofNullable(message);
        } catch (EmptyResultDataAccessException ex) {
            // 只有“查不到”才返回空；连接失败等异常继续抛出，避免被误判为消息不存在。
            return Optional.empty();
        }
    }

    /**
     * 查询到期可派发消息。
     */
    @Override
    public List<ReliableMessage> findDueMessages(Instant now, int limit) {
        return jdbcOperations.query("""
                        select message_id, topic, tag, payload_json, headers_json, status, retry_times,
                               next_retry_at, last_error, created_at, updated_at
                          from %s
                         where status in (?, ?)
                           and (next_retry_at is null or next_retry_at <= ?)
                         order by next_retry_at asc, created_at asc
                         limit ?
                        """.formatted(tableName),
                mapper(),
                ReliableMessageStatus.PENDING.name(),
                ReliableMessageStatus.RETRYING.name(),
                timestamp(now),
                Math.max(limit, 1));
    }

    /**
     * 原子抢占到期消息。
     *
     * <p>先查询候选消息，再按 message_id、status、next_retry_at 条件更新为 SENDING。并发实例抢占同一条消息时，
     * 只有一个实例能更新成功；失败的实例跳过该消息。</p>
     */
    @Override
    public List<ReliableMessage> claimDueMessages(Instant now, int limit) {
        return claimDueMessages(now, limit, null);
    }

    /**
     * 原子抢占到期消息，并回收发送超时消息。
     *
     * <p>回收 SENDING 消息时 {@code retry_times + 1}：否则每次发送都卡住的消息会被无限回收，永远进不了死信。</p>
     */
    @Override
    public List<ReliableMessage> claimDueMessages(Instant now, int limit, Instant claimTimeoutAt) {
        List<ReliableMessage> candidates = findClaimCandidates(now, limit, claimTimeoutAt);
        List<ReliableMessage> claimed = new ArrayList<>();
        for (ReliableMessage candidate : candidates) {
            boolean reclaim = candidate.status() == ReliableMessageStatus.SENDING;
            ReliableMessage sending = reclaim ? candidate.reclaimed(now) : candidate.sending(now);
            int updated = jdbcOperations.update("""
                            update %s
                               set status = ?,
                                   retry_times = ?,
                                   claimed_at = ?,
                                   claim_owner = ?,
                                   updated_at = ?
                             where message_id = ?
                               and status = ?
                               and retry_times = ?
                               and (
                                   (status in (?, ?) and (next_retry_at is null or next_retry_at <= ?))
                                   or (status = ? and claimed_at is not null and claimed_at <= ?)
                               )
                            """.formatted(tableName),
                    sending.status().name(),
                    sending.retryTimes(),
                    timestamp(now),
                    claimOwner,
                    timestamp(sending.updatedAt()),
                    candidate.message().messageId(),
                    candidate.status().name(),
                    candidate.retryTimes(),
                    ReliableMessageStatus.PENDING.name(),
                    ReliableMessageStatus.RETRYING.name(),
                    timestamp(now),
                    ReliableMessageStatus.SENDING.name(),
                    timestamp(claimTimeoutAt));
            if (updated == 1) {
                claimed.add(sending);
            }
        }
        return claimed;
    }

    private List<ReliableMessage> findClaimCandidates(Instant now, int limit, Instant claimTimeoutAt) {
        if (claimTimeoutAt == null) {
            return findDueMessages(now, limit);
        }
        return jdbcOperations.query("""
                        select message_id, topic, tag, payload_json, headers_json, status, retry_times,
                               next_retry_at, last_error, created_at, updated_at
                          from %s
                         where (status in (?, ?) and (next_retry_at is null or next_retry_at <= ?))
                            or (status = ? and claimed_at is not null and claimed_at <= ?)
                         order by next_retry_at asc, created_at asc
                         limit ?
                        """.formatted(tableName),
                mapper(),
                ReliableMessageStatus.PENDING.name(),
                ReliableMessageStatus.RETRYING.name(),
                timestamp(now),
                ReliableMessageStatus.SENDING.name(),
                timestamp(claimTimeoutAt),
                Math.max(limit, 1));
    }

    /**
     * 更新 outbox 消息状态。
     */
    @Override
    public void update(ReliableMessage message) {
        jdbcOperations.update("""
                        update %s
                           set status = ?,
                               retry_times = ?,
                               next_retry_at = ?,
                               claimed_at = ?,
                               claim_owner = ?,
                               last_error = ?,
                               updated_at = ?
                         where message_id = ?
                        """.formatted(tableName),
                message.status().name(),
                message.retryTimes(),
                timestamp(message.nextRetryAt()),
                message.status() == ReliableMessageStatus.SENDING ? timestamp(message.updatedAt()) : null,
                message.status() == ReliableMessageStatus.SENDING ? claimOwner : null,
                message.lastError(),
                timestamp(message.updatedAt()),
                message.message().messageId());
    }

    /**
     * 按 claim owner 回写派发结果。
     *
     * <p>只有状态仍为 SENDING 且 {@code claim_owner} 是当前实例时才更新。实例 A 卡住后被实例 B 回收并发送成功，
     * A 迟到的失败结果不会再把 SENT 覆盖成 RETRYING。</p>
     */
    @Override
    public boolean completeClaim(ReliableMessage message) {
        int updated = jdbcOperations.update("""
                        update %s
                           set status = ?,
                               retry_times = ?,
                               next_retry_at = ?,
                               claimed_at = null,
                               claim_owner = null,
                               last_error = ?,
                               updated_at = ?
                         where message_id = ?
                           and status = ?
                           and claim_owner = ?
                        """.formatted(tableName),
                message.status().name(),
                message.retryTimes(),
                timestamp(message.nextRetryAt()),
                message.lastError(),
                timestamp(message.updatedAt()),
                message.message().messageId(),
                ReliableMessageStatus.SENDING.name(),
                claimOwner);
        return updated == 1;
    }

    /**
     * 统计指定状态的消息条数。
     */
    @Override
    public long countByStatus(ReliableMessageStatus status) {
        Long count = jdbcOperations.queryForObject(
                "select count(*) from %s where status = ?".formatted(tableName), Long.class, status.name());
        return count == null ? 0L : count;
    }

    /**
     * 分批删除早于指定时间的 SENT 消息。
     *
     * <p>先按 {@code (status, updated_at)} 索引查出一批 message_id 再删除，避免 {@code DELETE ... LIMIT}
     * 在 MySQL、PostgreSQL、H2 之间语法不一致，也避免一次删除大量数据长时间锁表。</p>
     */
    @Override
    public int deleteSentMessagesBefore(Instant sentBefore, int limit) {
        List<String> messageIds = jdbcOperations.queryForList("""
                        select message_id
                          from %s
                         where status = ?
                           and updated_at < ?
                         order by updated_at asc
                         limit ?
                        """.formatted(tableName),
                String.class,
                ReliableMessageStatus.SENT.name(),
                timestamp(sentBefore),
                Math.max(limit, 1));
        int deleted = 0;
        for (String messageId : messageIds) {
            deleted += jdbcOperations.update("delete from %s where message_id = ? and status = ?".formatted(tableName),
                    messageId,
                    ReliableMessageStatus.SENT.name());
        }
        return deleted;
    }

    /**
     * 表名会直接拼进 SQL，只允许普通标识符（可带 schema 前缀），防止配置项变成注入入口。
     */
    static String safeTableName(String tableName) {
        String value = tableName == null || tableName.isBlank() ? "chaos_mq_outbox" : tableName.trim();
        if (!TABLE_NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Illegal outbox table name: " + value);
        }
        return value;
    }

    private RowMapper<ReliableMessage> mapper() {
        return (rs, rowNum) -> toReliableMessage(rs);
    }

    private ReliableMessage toReliableMessage(ResultSet rs) throws SQLException {
        MessageEnvelope<Object> envelope = new MessageEnvelope<>(
                rs.getString("message_id"),
                rs.getString("topic"),
                rs.getString("tag"),
                readObject(rs.getString("payload_json")),
                readHeaders(rs.getString("headers_json")),
                instant(rs.getTimestamp("created_at"))
        );
        return new ReliableMessage(
                envelope,
                ReliableMessageStatus.valueOf(rs.getString("status")),
                rs.getInt("retry_times"),
                instant(rs.getTimestamp("next_retry_at")),
                rs.getString("last_error"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at"))
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("outbox message json serialize failed", ex);
        }
    }

    private Object readObject(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            throw new IllegalStateException("outbox payload json deserialize failed", ex);
        }
    }

    private Map<String, String> readHeaders(String json) {
        try {
            return objectMapper.readValue(json, STRING_MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("outbox headers json deserialize failed", ex);
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
