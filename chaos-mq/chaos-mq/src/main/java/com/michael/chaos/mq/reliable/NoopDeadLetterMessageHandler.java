package com.michael.chaos.mq.reliable;

/**
 * 默认死信处理器。
 *
 * <p>默认实现不做外部动作，业务系统应替换为告警、审计或死信表实现。</p>
 */
public class NoopDeadLetterMessageHandler implements DeadLetterMessageHandler {

    /**
     * 忽略死信消息。
     */
    @Override
    public void handle(ReliableMessage message) {
        // 默认不做处理，避免核心契约模块依赖日志或外部系统。
    }
}
