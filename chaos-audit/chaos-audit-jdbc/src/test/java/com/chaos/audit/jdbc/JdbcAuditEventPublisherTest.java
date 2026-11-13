package com.chaos.audit.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chaos.audit.AuditAction;
import com.chaos.audit.AuditEvent;
import com.chaos.audit.AuditOutcome;
import com.chaos.audit.DefaultAuditAttributeSanitizer;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * JDBC 审计事件发布器测试。
 */
class JdbcAuditEventPublisherTest {

    @Test
    void applicationIdsWorkWithoutIdentityColumnsAndAuditSurvivesBusinessRollback() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:snowflake-audit;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE events (id BIGINT PRIMARY KEY, action VARCHAR(128), outcome VARCHAR(32), "
                + "principal_id VARCHAR(128), tenant_id VARCHAR(128), client_id VARCHAR(128), trace_id VARCHAR(128), "
                + "ip VARCHAR(64), uri VARCHAR(512), reason VARCHAR(512), occurred_at TIMESTAMP, attributes VARCHAR(4000))");
        var manager = new DataSourceTransactionManager(source);
        var independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var ids = new java.util.concurrent.atomic.AtomicLong(9007199254740993L);
        var publisher = new JdbcAuditEventPublisher(jdbc, new ObjectMapper(), "events", true,
                new DefaultAuditAttributeSanitizer(), independent, ids::getAndIncrement);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            publisher.publish(AuditEvent.builder(AuditAction.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS)
                    .attributes(Map.of("password", "synthetic-secret")).build());
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT id FROM events", Long.class)).isEqualTo(9007199254740993L);
        assertThat(jdbc.queryForObject("SELECT attributes FROM events", String.class)).doesNotContain("synthetic-secret");
    }

    /**
     * 审计事件应完整写入表，并在落库前脱敏敏感扩展属性。
     */
    @Test
    void shouldPersistAuditEventAndSanitizeAttributes() throws Exception {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        JdbcAuditEventPublisher publisher = new JdbcAuditEventPublisher(
                jdbcTemplate,
                new ObjectMapper(),
                "chaos_audit_event",
                true,
                new DefaultAuditAttributeSanitizer()
        );

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("grantType", "password");
        attributes.put("password", "123456");
        AuditEvent event = AuditEvent.builder(AuditAction.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS)
                .principalId("1001")
                .tenantId("tenant-a")
                .clientId("client-a")
                .traceId("trace-1")
                .ip("127.0.0.1")
                .uri("/oauth2/token")
                .occurredAt(Instant.parse("2026-05-23T10:00:00Z"))
                .attributes(attributes)
                .build();

        publisher.publish(event);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM chaos_audit_event WHERE trace_id = ?", "trace-1");
        assertThat(row.get("ACTION")).isEqualTo(AuditAction.AUTH_LOGIN_SUCCESS);
        assertThat(row.get("OUTCOME")).isEqualTo(AuditOutcome.SUCCESS.name());
        assertThat(row.get("PRINCIPAL_ID")).isEqualTo("1001");
        assertThat(row.get("TENANT_ID")).isEqualTo("tenant-a");
        assertThat(row.get("CLIENT_ID")).isEqualTo("client-a");
        assertThat(row.get("IP")).isEqualTo("127.0.0.1");
        assertThat(row.get("URI")).isEqualTo("/oauth2/token");
        assertThat(row.get("ATTRIBUTES").toString()).contains("\"grantType\":\"password\"");
        assertThat(row.get("ATTRIBUTES").toString()).contains("\"password\":\"******\"");
    }

    /**
     * 自定义表名必须经过白名单校验，避免配置被拼接成 SQL 注入入口。
     */
    @Test
    void shouldRejectUnsafeTableName() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource());

        assertThatThrownBy(() -> new JdbcAuditEventPublisher(
                jdbcTemplate,
                new ObjectMapper(),
                "chaos_audit_event;drop table users",
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");
    }

    /**
     * 超长字段必须截断后写入，不能因为 URI 或原因过长导致审计丢失。
     */
    @Test
    void shouldTruncateOverlongFields() throws Exception {
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        JdbcAuditEventPublisher publisher = new JdbcAuditEventPublisher(
                jdbcTemplate, new ObjectMapper(), "chaos_audit_event", true, new DefaultAuditAttributeSanitizer());
        AuditEvent event = AuditEvent.builder(AuditAction.AUTH_LOGIN_FAILURE, AuditOutcome.FAILURE)
                .traceId("trace-long")
                .uri("/" + "a".repeat(2000))
                .reason("r".repeat(2000))
                .build();

        publisher.publish(event);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM chaos_audit_event WHERE trace_id = ?", "trace-long");
        assertThat(row.get("URI").toString()).hasSize(512);
        assertThat(row.get("REASON").toString()).hasSize(512);
    }

    /**
     * 使用 REQUIRES_NEW 事务模板时，业务事务回滚不能带走审计记录。
     */
    @Test
    void auditShouldSurviveOuterTransactionRollbackWithIndependentTransaction() throws Exception {
        DataSource dataSource = dataSource();
        JdbcTemplate jdbcTemplate = createJdbcTemplate();
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        JdbcAuditEventPublisher publisher = new JdbcAuditEventPublisher(
                new JdbcTemplate(dataSource), new ObjectMapper(), "chaos_audit_event", true,
                new DefaultAuditAttributeSanitizer(), requiresNew);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publish(AuditEvent.builder(AuditAction.AUTH_LOGIN_FAILURE, AuditOutcome.FAILURE)
                    .traceId("trace-rollback").build());
            status.setRollbackOnly();
        });

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chaos_audit_event WHERE trace_id = ?", Integer.class, "trace-rollback")).isEqualTo(1);
    }

    private static JdbcTemplate createJdbcTemplate() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/chaos-audit-schema.sql"));
        }
        return new JdbcTemplate(dataSource);
    }

    private static DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:chaos_audit;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
