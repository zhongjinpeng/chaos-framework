package com.michael.chaos.autoconfigure.audit.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.michael.chaos.audit.AuditAttributeSanitizer;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.DefaultAuditAttributeSanitizer;
import com.michael.chaos.audit.jdbc.JdbcAuditEventPublisher;
import com.michael.chaos.autoconfigure.audit.AuditPublishers;
import com.michael.chaos.autoconfigure.audit.ChaosAuditAutoConfiguration;
import com.michael.chaos.autoconfigure.audit.ChaosAuditProperties;
import com.michael.chaos.core.metrics.ChaosMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC 审计持久化自动装配。
 *
 * <p>该自动装配先于默认日志审计自动装配执行。只有显式开启 `chaos.audit.jdbc.enabled=true`
 * 且存在 `JdbcOperations` 时，才注册 JDBC 发布器覆盖默认日志发布器。</p>
 *
 * <p>顺序声明拆成 {@code @AutoConfiguration(beforeName)} 与 {@code @AutoConfigureAfter} 两处，
 * 语义等价于在 {@code @AutoConfiguration} 中同时声明 before/after，保持与架构约束测试一致。</p>
 */
@AutoConfiguration(beforeName = "com.michael.chaos.autoconfigure.audit.ChaosAuditAutoConfiguration")
@AutoConfigureAfter(name = {
        "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
})
@ConditionalOnClass({AuditEventPublisher.class, JdbcAuditEventPublisher.class, JdbcOperations.class})
@ConditionalOnProperty(prefix = "chaos.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({ChaosAuditJdbcProperties.class, ChaosAuditProperties.class})
public class ChaosAuditJdbcAutoConfiguration {

    /**
     * 注册可配置关键字的审计属性脱敏器。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditAttributeSanitizer auditAttributeSanitizer(ChaosAuditJdbcProperties properties) {
        return new DefaultAuditAttributeSanitizer(properties.getSensitiveKeywords(), properties.getMaskValue());
    }

    /**
     * 注册 JDBC 审计事件发布器。
     *
     * <p>{@code chaos.audit.jdbc.independent-transaction=true}（默认）且存在事务管理器时，
     * 审计在 {@code REQUIRES_NEW} 独立事务中写入，业务回滚不会带走审计记录。</p>
     */
    @Bean
    @ConditionalOnBean(JdbcOperations.class)
    @ConditionalOnMissingBean(AuditEventPublisher.class)
    @ConditionalOnProperty(prefix = "chaos.audit.jdbc", name = "enabled", havingValue = "true")
    public AuditEventPublisher jdbcAuditEventPublisher(
            JdbcOperations jdbcOperations,
            ObjectMapper objectMapper,
            AuditAttributeSanitizer attributeSanitizer,
            ChaosAuditJdbcProperties properties,
            ChaosAuditProperties auditProperties,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider,
            ObjectProvider<ChaosMetrics> metricsProvider) {
        TransactionTemplate transactionTemplate = null;
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfUnique();
        if (properties.isIndependentTransaction() && transactionManager != null) {
            transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
        // JDBC 写入是同步 IO，异步包装对它的价值最大：扫描器打未授权接口时每条审计都是一次 inline INSERT。
        return AuditPublishers.wrap(
                new JdbcAuditEventPublisher(
                        jdbcOperations,
                        objectMapper,
                        properties.getTableName(),
                        properties.isFailFast(),
                        attributeSanitizer,
                        transactionTemplate),
                auditProperties,
                metricsProvider.getIfAvailable()
        );
    }
}
