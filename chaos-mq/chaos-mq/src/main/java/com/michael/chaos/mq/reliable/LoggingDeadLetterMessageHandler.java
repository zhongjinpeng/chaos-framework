package com.michael.chaos.mq.reliable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 以 ERROR 日志记录死信的处理器。
 *
 * <p>自动装配默认使用该实现而不是 {@link NoopDeadLetterMessageHandler}，保证死信至少能被日志告警发现。
 * 日志只记录消息 ID、topic、重试次数和错误，不输出 payload，避免把业务敏感数据写进日志。</p>
 */
public class LoggingDeadLetterMessageHandler implements DeadLetterMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingDeadLetterMessageHandler.class);

    /**
     * 记录死信消息。
     */
    @Override
    public void handle(ReliableMessage message) {
        log.error("Outbox message moved to dead letter, messageId={}, topic={}, retryTimes={}, lastError={}",
                message.message().messageId(),
                message.message().topic(),
                message.retryTimes(),
                message.lastError());
    }
}
