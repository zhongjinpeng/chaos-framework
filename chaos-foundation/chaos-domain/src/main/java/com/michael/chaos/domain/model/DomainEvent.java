package com.michael.chaos.domain.model;

import java.time.Instant;

/**
 * 领域事件基础契约。
 */
public interface DomainEvent {

    /**
     * 返回事件唯一标识。
     */
    String eventId();

    /**
     * 返回产生该事件的聚合标识。
     */
    String aggregateId();

    /**
     * 返回事件发生时间。
     */
    Instant occurredAt();
}
