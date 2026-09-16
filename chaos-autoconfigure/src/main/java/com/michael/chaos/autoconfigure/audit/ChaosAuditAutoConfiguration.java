package com.michael.chaos.autoconfigure.audit;

import com.michael.chaos.audit.AuditAttributeSanitizer;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.DefaultAuditAttributeSanitizer;
import com.michael.chaos.audit.LoggingAuditEventPublisher;
import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.core.metrics.ChaosMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 审计自动装配。
 */
@AutoConfiguration
@ConditionalOnClass(AuditEventPublisher.class)
@EnableConfigurationProperties(ChaosAuditProperties.class)
public class ChaosAuditAutoConfiguration {

    /**
     * 注册默认审计属性脱敏器，所有发布器共享同一套脱敏策略。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditAttributeSanitizer auditAttributeSanitizer() {
        return new DefaultAuditAttributeSanitizer();
    }

    /**
     * 注册默认日志审计发布器，输出前对扩展属性脱敏。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AuditEventPublisher auditEventPublisher(
            AuditAttributeSanitizer auditAttributeSanitizer,
            ChaosAuditProperties properties,
            ObjectProvider<ChaosMetrics> metricsProvider) {
        return AuditPublishers.wrap(
                new LoggingAuditEventPublisher(auditAttributeSanitizer), properties, metricsProvider.getIfAvailable());
    }

    /**
     * 审计关闭时注册空发布器，避免下游组件做空判断。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.audit", name = "enabled", havingValue = "false")
    public AuditEventPublisher noopAuditEventPublisher() {
        return new NoopAuditEventPublisher();
    }
}
