package com.michael.chaos.mq.jdbc;

import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * 启动时检查 outbox 表是否可访问。
 *
 * <p>引入 MQ 与 JDBC 后派发器默认启动，如果没有建表，派发线程会每隔几秒输出一条 SQL 语法错误，
 * 使用方很难从 {@code Table "CHAOS_MQ_OUTBOX" not found} 联想到需要执行哪个脚本。这里在启动时检查一次，
 * 缺表时给出与数据库方言对应的建表脚本路径。</p>
 *
 * <p>检查失败只返回诊断信息、不抛异常：数据库暂时不可用时应用仍可启动，派发器恢复后会继续工作。</p>
 */
public final class OutboxSchemaVerifier {

    /**
     * 建表脚本在 chaos-mq-jdbc jar 中的目录。
     */
    public static final String SCHEMA_LOCATION = "classpath:db/";

    private OutboxSchemaVerifier() {
    }

    /**
     * 检查 outbox 表。
     *
     * @param jdbcOperations JDBC 操作入口
     * @param tableName outbox 表名
     * @return 表不可访问时返回诊断信息
     */
    public static Optional<ChaosDiagnostic> verify(JdbcOperations jdbcOperations, String tableName) {
        Objects.requireNonNull(jdbcOperations, "jdbcOperations must not be null");
        String table = JdbcOutboxMessageRepository.safeTableName(tableName);
        try {
            jdbcOperations.queryForObject("select count(*) from " + table + " where 1 = 0", Long.class);
            return Optional.empty();
        } catch (DataAccessException ex) {
            String script = schemaScript(databaseProduct(jdbcOperations));
            return Optional.of(new ChaosDiagnostic(
                    "outbox 表 " + table + " 不可访问，可靠消息无法写入或派发",
                    List.of("数据库返回：" + rootMessage(ex),
                            "引入 chaos-mq-starter 且存在 JdbcTemplate 时会启用 JDBC outbox 与派发器"),
                    List.of("执行建表脚本 " + SCHEMA_LOCATION + script + "（位于 chaos-mq-jdbc jar 中）或把它加入 Flyway/Liquibase 迁移",
                            "表名不是默认值时同步配置 chaos.mq.outbox.table-name",
                            "暂不使用 outbox：设置 chaos.mq.outbox.enabled=false")));
        }
    }

    /**
     * 按数据库产品名选择建表脚本。
     */
    static String schemaScript(String databaseProduct) {
        String product = databaseProduct == null ? "" : databaseProduct.toLowerCase(Locale.ROOT);
        if (product.contains("mysql") || product.contains("mariadb")) {
            return "chaos-mq-outbox-schema-mysql.sql";
        }
        if (product.contains("postgres")) {
            return "chaos-mq-outbox-schema-postgresql.sql";
        }
        if (product.contains("h2")) {
            return "chaos-mq-outbox-schema-h2.sql";
        }
        return "chaos-mq-outbox-schema-{mysql,postgresql,h2}.sql";
    }

    private static String databaseProduct(JdbcOperations jdbcOperations) {
        try {
            return jdbcOperations.execute((ConnectionCallback<String>) connection -> {
                DatabaseMetaData metaData = connection.getMetaData();
                return metaData == null ? "" : metaData.getDatabaseProductName();
            });
        } catch (DataAccessException ex) {
            return "";
        }
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message.lines().findFirst().orElse(message);
    }
}
