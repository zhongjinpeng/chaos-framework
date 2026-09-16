package com.michael.chaos.mq.reliable;

import com.michael.chaos.mq.MessageEnvelope;

/**
 * 基于 outbox 的可靠消息发布入口。
 *
 * <p>业务事务内调用该发布器只写 outbox，不直接调用 MQ。{@link ReliableMessageDispatcher} 再异步读取 outbox
 * 并调用真实 {@link com.michael.chaos.mq.MessagePublisher} 发送，降低本地事务和 MQ 发送之间的不一致风险。</p>
 *
 * <p>该类只实现 {@link OutboxPublisher}，不再实现 {@code MessagePublisher}，
 * 避免它顶替真实 MQ 发布器导致派发器无法发送。</p>
 */
public class ReliableMessagePublisher implements OutboxPublisher {

    private final OutboxMessageRepository repository;

    /**
     * 创建可靠消息发布器。
     */
    public ReliableMessagePublisher(OutboxMessageRepository repository) {
        this.repository = repository;
    }

    /**
     * 保存待发送消息到 outbox，并合并当前请求的 trace 头。
     */
    @Override
    public void publish(MessageEnvelope<?> message) {
        repository.save(ReliableMessage.pending(message.withTraceHeaders()));
    }
}
