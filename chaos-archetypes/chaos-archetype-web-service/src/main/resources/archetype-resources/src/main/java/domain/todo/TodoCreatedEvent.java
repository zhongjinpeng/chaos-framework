package ${package}.domain.todo;

import com.michael.chaos.domain.model.DomainEvent;
import java.time.Instant;

/**
 * 待办已创建领域事件。
 *
 * @param eventId 事件 ID；接入 chaos-mq-starter 后可直接作为 outbox 消息 ID，便于消费端幂等
 * @param aggregateId 待办 ID
 * @param tenantId 租户 ID
 * @param occurredAt 发生时间
 */
public record TodoCreatedEvent(String eventId, String aggregateId, String tenantId, Instant occurredAt)
        implements DomainEvent {
}
