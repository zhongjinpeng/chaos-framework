package com.michael.chaos.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AggregateRootTest {

    @Test
    void initiallyHasNoEvents() {
        TestAggregate aggregate = new TestAggregate("1");
        assertTrue(aggregate.domainEvents().isEmpty());
    }

    @Test
    void registerEventAddsToList() {
        TestAggregate aggregate = new TestAggregate("1");
        DomainEvent event = new TestEvent("evt-1", "1");
        aggregate.doSomething(event);

        assertEquals(1, aggregate.domainEvents().size());
        assertSame(event, aggregate.domainEvents().get(0));
    }

    @Test
    void domainEventsReturnsUnmodifiableList() {
        TestAggregate aggregate = new TestAggregate("1");
        aggregate.doSomething(new TestEvent("evt-1", "1"));

        List<DomainEvent> events = aggregate.domainEvents();
        assertThrows(UnsupportedOperationException.class, () -> events.add(new TestEvent("evt-2", "1")));
    }

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
