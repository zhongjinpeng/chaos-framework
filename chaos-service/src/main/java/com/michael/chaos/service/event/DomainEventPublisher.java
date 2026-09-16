package com.michael.chaos.service.event;

import com.michael.chaos.domain.model.DomainEvent;
import java.util.Collection;

/**
 * 领域事件发布端口。
 */
public interface DomainEventPublisher {

    /**
     * 发布单个领域事件。
     *
     * @param event 领域事件
     */
    void publish(DomainEvent event);

    /**
     * 批量发布领域事件。
     *
     * @param events 领域事件集合
     */
    default void publishAll(Collection<? extends DomainEvent> events) {
        events.forEach(this::publish);
    }
}
