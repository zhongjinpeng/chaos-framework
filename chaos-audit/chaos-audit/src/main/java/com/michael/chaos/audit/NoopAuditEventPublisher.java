package com.michael.chaos.audit;

/**
 * 空审计发布器。
 *
 * <p>用于显式关闭审计或测试场景，避免调用方写空判断。</p>
 */
public class NoopAuditEventPublisher implements AuditEventPublisher {

    /**
     * 忽略审计事件。
     */
    @Override
    public void publish(AuditEvent event) {
        // 审计关闭时不执行任何动作。
    }
}
