package com.michael.chaos.audit;

/**
 * 审计事件发布器。
 *
 * <p>框架只依赖该端口；业务侧可以替换为数据库、Kafka、RocketMQ 或专用审计中心实现。</p>
 */
@FunctionalInterface
public interface AuditEventPublisher {

    /**
     * 发布审计事件。
     *
     * @param event 审计事件
     */
    void publish(AuditEvent event);
}
