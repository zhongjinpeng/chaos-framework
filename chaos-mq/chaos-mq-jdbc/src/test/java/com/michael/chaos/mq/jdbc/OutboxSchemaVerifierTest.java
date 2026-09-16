package com.michael.chaos.mq.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * outbox 表检查测试。
 */
class OutboxSchemaVerifierTest {

    private final EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
            .generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2)
            .build();

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    /**
     * 表存在时不产生诊断。
     */
    @Test
    void shouldPassWhenTableExists() {
        new ResourceDatabasePopulator(new ClassPathResource("db/chaos-mq-outbox-schema-h2.sql")).execute(database);

        assertThat(OutboxSchemaVerifier.verify(new JdbcTemplate(database), "chaos_mq_outbox")).isEmpty();
    }

    /**
     * 缺表时给出与方言对应的建表脚本和关闭开关。
     */
    @Test
    void shouldPointToDialectScriptWhenTableIsMissing() {
        Optional<ChaosDiagnostic> diagnostic = OutboxSchemaVerifier.verify(new JdbcTemplate(database), "chaos_mq_outbox");

        assertThat(diagnostic).isPresent();
        assertThat(diagnostic.get().format())
                .startsWith("问题：outbox 表 chaos_mq_outbox 不可访问")
                .contains("classpath:db/chaos-mq-outbox-schema-h2.sql")
                .contains("chaos.mq.outbox.enabled=false");
    }

    /**
     * 按数据库产品名选择脚本。
     */
    @Test
    void shouldSelectScriptByDatabaseProduct() {
        assertThat(OutboxSchemaVerifier.schemaScript("MySQL")).isEqualTo("chaos-mq-outbox-schema-mysql.sql");
        assertThat(OutboxSchemaVerifier.schemaScript("MariaDB")).isEqualTo("chaos-mq-outbox-schema-mysql.sql");
        assertThat(OutboxSchemaVerifier.schemaScript("PostgreSQL")).isEqualTo("chaos-mq-outbox-schema-postgresql.sql");
        assertThat(OutboxSchemaVerifier.schemaScript("Oracle")).contains("{mysql,postgresql,h2}");
    }
}
