package com.michael.chaos.examples.order.domain;

import com.michael.chaos.domain.model.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 订单已创建领域事件。
 *
 * @param eventId 事件 ID，同时作为 outbox 消息 ID，便于消费端幂等
 * @param aggregateId 订单编号
 * @param tenantId 租户 ID
 * @param buyerId 买家 ID
 * @param amount 订单金额
 * @param occurredAt 发生时间
 */
public record OrderCreatedEvent(
        String eventId,
        String aggregateId,
        String tenantId,
        String buyerId,
        BigDecimal amount,
        Instant occurredAt
) implements DomainEvent {

    /**
     * 消息主题。
     */
    public static final String TOPIC = "example.order.created";

    /**
     * 根据订单创建事件。
     */
    static OrderCreatedEvent of(Order order, Instant occurredAt) {
        return new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                order.orderNo(),
                order.tenantId(),
                order.buyerId(),
                order.amount(),
                occurredAt);
    }
}
