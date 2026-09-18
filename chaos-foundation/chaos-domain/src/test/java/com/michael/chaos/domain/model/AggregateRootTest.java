package com.michael.chaos.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AggregateRootTest {

    /**
     * 新建聚合不应该自带事件，否则第一次保存就会发出莫名其妙的领域事件。
     */
    @Test
    void initiallyHasNoEvents() {
        TestAggregate aggregate = new TestAggregate("1");
        assertTrue(aggregate.domainEvents().isEmpty());
    }

    /**
     * 注册的领域事件要能被取出，这是事件发布的前提。
     */
    @Test
    void registerEventAddsToList() {
        TestAggregate aggregate = new TestAggregate("1");
        DomainEvent event = new TestEvent("evt-1", "1");
        aggregate.doSomething(event);

        assertEquals(1, aggregate.domainEvents().size());
        assertSame(event, aggregate.domainEvents().get(0));
    }

    /**
     * 事件列表对外只读，外部只能通过聚合自己的方法注册，保证事件与状态变更同源。
     */
    @Test
    void domainEventsReturnsUnmodifiableList() {
        TestAggregate aggregate = new TestAggregate("1");
        aggregate.doSomething(new TestEvent("evt-1", "1"));

        List<DomainEvent> events = aggregate.domainEvents();
        assertThrows(UnsupportedOperationException.class, () -> events.add(new TestEvent("evt-2", "1")));
    }

    /**
     * 事件发布后必须清空，否则同一个聚合再次保存会重复发送。
     */
    @Test
    void clearDomainEventsEmptiesTheList() {
        TestAggregate aggregate = new TestAggregate("1");
        aggregate.doSomething(new TestEvent("evt-1", "1"));
        aggregate.doSomething(new TestEvent("evt-2", "1"));

        assertEquals(2, aggregate.domainEvents().size());
        aggregate.clearDomainEvents();
        assertTrue(aggregate.domainEvents().isEmpty());
    }

    // --- Test fixtures ---

    private static class TestAggregate extends AggregateRoot<String> {
        private final String id;

        TestAggregate(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        void doSomething(DomainEvent event) {
            registerEvent(event);
        }
    }

    private record TestEvent(String eventId, String aggregateId) implements DomainEvent {
        @Override
        public Instant occurredAt() {
            return Instant.now();
        }
    }
}
