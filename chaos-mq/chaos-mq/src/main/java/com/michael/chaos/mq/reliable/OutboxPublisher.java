package com.michael.chaos.mq.reliable;

import com.michael.chaos.mq.MessageEnvelope;

/**
 * outbox 写入端口。
 *
 * <p>业务事务内调用，只把消息写入 outbox 表，不直接访问 MQ。刻意与 {@link com.michael.chaos.mq.MessagePublisher}
 * 分成两个接口：如果二者共用同一接口，写 outbox 的 Bean 会让 {@code @ConditionalOnMissingBean}
 * 跳过真实 MQ 发布器，派发器就拿不到真正的 transport。</p>
 */
@FunctionalInterface
public interface OutboxPublisher {

    /**
     * 把消息保存到 outbox，等待派发器异步发送。
     *
     * <p>必须在业务事务内调用，才能保证业务数据与消息同时提交或同时回滚。</p>
     */
    void publish(MessageEnvelope<?> message);
}
