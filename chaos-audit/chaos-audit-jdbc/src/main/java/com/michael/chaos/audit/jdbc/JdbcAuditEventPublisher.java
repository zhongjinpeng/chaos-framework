package com.michael.chaos.audit.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.michael.chaos.audit.AuditAttributeSanitizer;
import com.michael.chaos.audit.AuditEvent;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.DefaultAuditAttributeSanitizer;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 基于 JDBC 的审计事件发布器。
 *
 * <p>该实现属于基础设施适配器，只依赖 `AuditEventPublisher` 端口和 Spring JDBC。
 * 默认不会被 `chaos-audit-starter` 装配，只有引用方引入 JDBC starter 并显式开启配置后才会落库。</p>
 *
 * <p>可靠性设计：</p>
 * <ul>
 *     <li><b>独立事务</b>：传入 {@code REQUIRES_NEW} 的 {@link TransactionOperations} 时，审计写入不再加入调用方事务。
 *     原实现同步加入业务事务，业务回滚时 FAILURE/DENIED 审计一起丢失；在 PostgreSQL 上审计 SQL 失败后
 *     外层事务进入 aborted 状态，即使异常被吞掉，后续业务 SQL 也全部失败。</li>
 *     <li><b>字段截断</b>：写入前按表结构长度截断，避免 MySQL 严格模式下超长 URI/reason 导致写入失败而丢审计。</li>
 * </ul>
 */
public class JdbcAuditEventPublisher implements AuditEventPublisher {

    /**
     * 默认审计表名。
     */
    public static final String DEFAULT_TABLE_NAME = "chaos_audit_event";

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditEventPublisher.class);

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * 与 db/chaos-audit-schema.sql 保持一致的字段长度。
     */
    static final int ACTION_LENGTH = 128;
    static final int OUTCOME_LENGTH = 32;
    static final int IDENTIFIER_LENGTH = 128;
    static final int IP_LENGTH = 64;
    static final int URI_LENGTH = 512;
    static final int REASON_LENGTH = 512;

    private final JdbcOperations jdbcOperations;

    private final ObjectMapper objectMapper;

    private final AuditAttributeSanitizer attributeSanitizer;

    private final boolean failFast;

    private final String tableName;

    private final String insertSql;

    private final TransactionOperations transactionOperations;

    /**
     * 使用默认表名和非 fail-fast 模式创建发布器。
     */
    public JdbcAuditEventPublisher(JdbcOperations jdbcOperations, ObjectMapper objectMapper) {
        this(jdbcOperations, objectMapper, DEFAULT_TABLE_NAME, false, new DefaultAuditAttributeSanitizer());
    }

    /**
     * 使用自定义表名和失败策略创建发布器。
     */
    public JdbcAuditEventPublisher(
            JdbcOperations jdbcOperations,
            ObjectMapper objectMapper,
            String tableName,
            boolean failFast) {
        this(jdbcOperations, objectMapper, tableName, failFast, new DefaultAuditAttributeSanitizer());
    }

    /**
     * 使用自定义脱敏器创建发布器，审计写入加入调用方当前事务（如有）。
     */
    public JdbcAuditEventPublisher(
            JdbcOperations jdbcOperations,
            ObjectMapper objectMapper,
            String tableName,
            boolean failFast,
            AuditAttributeSanitizer attributeSanitizer) {
        this(jdbcOperations, objectMapper, tableName, failFast, attributeSanitizer, null);
    }

    /**
     * 使用完整参数创建发布器。
     *
     * @param transactionOperations 审计写入使用的事务模板，推荐传播行为为 {@code REQUIRES_NEW}；
     *                              为 {@code null} 时直接执行（加入调用方事务）
     */
    public JdbcAuditEventPublisher(
            JdbcOperations jdbcOperations,
            ObjectMapper objectMapper,
            String tableName,
            boolean failFast,
            AuditAttributeSanitizer attributeSanitizer,
            TransactionOperations transactionOperations) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "jdbcOperations must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.attributeSanitizer = Objects.requireNonNull(attributeSanitizer, "attributeSanitizer must not be null");
        this.failFast = failFast;
        this.tableName = normalizeTableName(tableName);
        this.insertSql = buildInsertSql(this.tableName);
        this.transactionOperations = transactionOperations;
    }

    /**
     * 将审计事件写入数据库。
     */
    @Override
    public void publish(AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        try {
            if (transactionOperations == null) {
                insert(event);
            } else {
                transactionOperations.executeWithoutResult(status -> insert(event));
            }
        } catch (RuntimeException ex) {
            handlePublishFailure(event, ex);
        }
    }

    /**
     * 返回最终使用的审计表名，主要用于测试和运行时诊断。
     */
    public String tableName() {
        return tableName;
    }

    private void insert(AuditEvent event) {
        jdbcOperations.update(
                insertSql,
                truncate(event.action(), ACTION_LENGTH),
                truncate(event.outcome().name(), OUTCOME_LENGTH),
                truncate(event.principalId(), IDENTIFIER_LENGTH),
                truncate(event.tenantId(), IDENTIFIER_LENGTH),
                truncate(event.clientId(), IDENTIFIER_LENGTH),
                truncate(event.traceId(), IDENTIFIER_LENGTH),
                truncate(event.ip(), IP_LENGTH),
                truncate(event.uri(), URI_LENGTH),
                truncate(event.reason(), REASON_LENGTH),
                Timestamp.from(event.occurredAt()),
                toJson(attributeSanitizer.sanitize(event.attributes()))
        );
    }

    /**
     * 按字符数截断；数据库字段长度以字符计（MySQL utf8mb4 VARCHAR、PostgreSQL VARCHAR 均如此）。
     */
    static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private String toJson(Map<String, String> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes == null ? Map.of() : attributes);
        } catch (JsonProcessingException ex) {
            if (failFast) {
                throw new IllegalStateException("审计扩展属性序列化失败", ex);
            }
            log.warn("审计扩展属性序列化失败，已使用空 JSON 兜底", ex);
            return "{}";
        }
    }

    private void handlePublishFailure(AuditEvent event, RuntimeException ex) {
        if (failFast) {
            throw ex;
        }
        log.warn(
                "审计事件落库失败，已按非 fail-fast 策略忽略。action={} traceId={}",
                event.action(),
                event.traceId(),
                ex
        );
    }

    private static String buildInsertSql(String tableName) {
        return """
                INSERT INTO %s (
                    action,
                    outcome,
                    principal_id,
                    tenant_id,
                    client_id,
                    trace_id,
                    ip,
                    uri,
                    reason,
                    occurred_at,
                    attributes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(tableName).replaceAll("\\s+", " ").trim();
    }

    private static String normalizeTableName(String tableName) {
        String normalized = tableName == null ? "" : tableName.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        String[] parts = normalized.split("\\.", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("tableName only supports table or schema.table format");
        }
        for (String part : parts) {
            if (!IDENTIFIER_PATTERN.matcher(part).matches()) {
                throw new IllegalArgumentException("tableName contains illegal identifier: " + tableName);
            }
        }
        return normalized;
    }
}
