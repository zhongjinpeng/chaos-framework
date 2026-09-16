package com.michael.chaos.autoconfigure.audit;

import com.michael.chaos.audit.AsyncAuditEventPublisher;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.core.metrics.ChaosMetrics;

/**
 * 审计发布器装配辅助。
 *
 * <p>日志发布器与 JDBC 发布器由两个自动装配分别注册，异步包装对两者都适用。
 * 把包装逻辑收在这里，避免两处各写一遍导致行为漂移——例如只给其中一个加了异步。</p>
 */
public final class AuditPublishers {

    private AuditPublishers() {
    }

    /**
     * 按配置决定是否用异步发布器包装。
     *
     * @param delegate 真正执行写入的发布器
     * @param properties 审计配置
     * @param metrics 治理指标上报端口，可为 {@code null}
     * @return 开启异步时返回包装后的发布器，否则原样返回
     */
    public static AuditEventPublisher wrap(
            AuditEventPublisher delegate, ChaosAuditProperties properties, ChaosMetrics metrics) {
        if (!properties.getAsync().isEnabled()) {
            return delegate;
        }
        return new AsyncAuditEventPublisher(delegate, properties.getAsync().getQueueCapacity(), metrics);
    }
}
