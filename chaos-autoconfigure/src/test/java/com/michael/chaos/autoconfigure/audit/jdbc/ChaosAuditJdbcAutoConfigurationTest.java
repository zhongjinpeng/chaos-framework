package com.michael.chaos.autoconfigure.audit.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.michael.chaos.audit.AuditAttributeSanitizer;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.LoggingAuditEventPublisher;
import com.michael.chaos.audit.jdbc.JdbcAuditEventPublisher;
import com.michael.chaos.autoconfigure.audit.ChaosAuditAutoConfiguration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * JDBC 审计自动装配测试。
 */
class ChaosAuditJdbcAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ChaosAuditJdbcAutoConfiguration.class,
                    ChaosAuditAutoConfiguration.class
            ))
            .withUserConfiguration(JdbcConfiguration.class);

    private final ApplicationContextRunner bootJdbcContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JdbcTemplateAutoConfiguration.class,
                    JacksonAutoConfiguration.class,
                    ChaosAuditJdbcAutoConfiguration.class,
                    ChaosAuditAutoConfiguration.class
            ))
            .withUserConfiguration(BootDataSourceConfiguration.class)
            .withPropertyValues("chaos.audit.jdbc.enabled=true");

    /**
     * 显式开启 JDBC 审计时，应优先注册 JDBC 发布器覆盖默认日志发布器。
     */
    @Test
    void shouldRegisterJdbcAuditPublisherWhenEnabled() {
        contextRunner.withPropertyValues("chaos.audit.jdbc.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditEventPublisher.class);
                    assertThat(context.getBean(AuditEventPublisher.class)).isInstanceOf(JdbcAuditEventPublisher.class);
                    assertThat(context).hasSingleBean(AuditAttributeSanitizer.class);
                    assertThat(context).hasSingleBean(ChaosAuditJdbcProperties.class);
                });
    }

    /**
     * 使用 Boot 自动创建 JdbcOperations 时，审计自动装配必须等待 JDBC Bean 就绪。
     */
    @Test
    void shouldRegisterJdbcAuditPublisherAfterBootJdbcAutoConfiguration() {
        bootJdbcContextRunner.run(context -> {
            assertThat(context).hasSingleBean(JdbcOperations.class);
            assertThat(context).hasSingleBean(AuditEventPublisher.class);
            assertThat(context.getBean(AuditEventPublisher.class)).isInstanceOf(JdbcAuditEventPublisher.class);
        });
    }

    /**
     * 未开启 JDBC 审计时，应回退到默认日志发布器。
     */
    @Test
    void shouldFallbackToLoggingAuditPublisherWhenJdbcDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuditEventPublisher.class);
            assertThat(context.getBean(AuditEventPublisher.class)).isInstanceOf(LoggingAuditEventPublisher.class);
        });
    }

    /**
     * 全局关闭审计时，JDBC 发布器不能绕过总开关。
     */
    @Test
    void shouldRespectGlobalAuditEnabledSwitch() {
        contextRunner.withPropertyValues(
                        "chaos.audit.enabled=false",
                        "chaos.audit.jdbc.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditEventPublisher.class);
                    assertThat(context.getBean(AuditEventPublisher.class)).isNotInstanceOf(JdbcAuditEventPublisher.class);
                });
    }

    /**
     * 业务侧提供审计发布器时，JDBC 自动装配必须让位。
     */
    @Test
    void shouldBackOffWhenCustomAuditPublisherProvided() {
        contextRunner.withUserConfiguration(CustomAuditPublisherConfiguration.class)
                .withPropertyValues("chaos.audit.jdbc.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditEventPublisher.class);
                    assertThat(context.getBean(AuditEventPublisher.class)).isNotInstanceOf(JdbcAuditEventPublisher.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcConfiguration {

        /**
         * 测试用内存数据源。
         */
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:chaos_audit_auto;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        /**
         * Spring JDBC 操作入口。
         */
        @Bean
        JdbcOperations jdbcOperations(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        /**
         * JSON 序列化器。
         */
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BootDataSourceConfiguration {

        /**
         * 只提供数据源，由 Boot JdbcTemplateAutoConfiguration 创建 JdbcOperations。
         */
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:chaos_audit_boot_auto;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomAuditPublisherConfiguration {

        /**
         * 业务自定义审计发布器。
         */
        @Bean
        AuditEventPublisher auditEventPublisher() {
            return event -> {
            };
        }
    }
}
