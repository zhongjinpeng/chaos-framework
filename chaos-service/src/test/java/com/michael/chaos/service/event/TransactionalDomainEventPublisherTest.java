package com.michael.chaos.service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.domain.model.DomainEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务后领域事件发布器测试。
 */
class TransactionalDomainEventPublisherTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * 有事务时延迟到提交后发布。
     */
    @Test
    void shouldPublishAfterCommitWhenTransactionActive() {
        List<Object> published = new ArrayList<>();
        TransactionalDomainEventPublisher publisher = new TransactionalDomainEventPublisher(published::add);
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(new SampleEvent());

        assertThat(published).isEmpty();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(published).hasSize(1);
    }

    /**
     * 提交后监听器异常不能传回调用方，否则调用方会误以为业务失败而重试。
     */
    @Test
    void shouldIsolateListenerFailureAfterCommit() {
        TransactionalDomainEventPublisher publisher = new TransactionalDomainEventPublisher(event -> {
            throw new IllegalStateException("listener failed");
        });
        TransactionSynchronizationManager.initSynchronization();
        publisher.publish(new SampleEvent());

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }

    /**
     * 无事务时立即发布，监听器异常按原样抛出。
     */
    @Test
    void shouldPropagateListenerFailureWithoutTransaction() {
        TransactionalDomainEventPublisher publisher = new TransactionalDomainEventPublisher(event -> {
            throw new IllegalStateException("listener failed");
        });

        assertThatThrownBy(() -> publisher.publish(new SampleEvent())).isInstanceOf(IllegalStateException.class);
    }

    private static final class SampleEvent implements DomainEvent {

        @Override
        public String eventId() {
            return "event-1";
        }

        @Override
        public String aggregateId() {
            return "order-1";
        }

        @Override
        public Instant occurredAt() {
            return Instant.now();
        }
    }
}
