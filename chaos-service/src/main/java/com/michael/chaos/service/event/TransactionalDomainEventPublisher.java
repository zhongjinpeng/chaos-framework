package com.michael.chaos.service.event;

import com.michael.chaos.domain.model.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后发布领域事件的实现。
 *
 * <p>如果当前存在活跃事务，事件在 AFTER_COMMIT 阶段发布，保证监听器看到的数据已持久化。
 * 如果没有活跃事务，立即发布。</p>
 *
 * <p><b>监听器约束（重要）：</b>AFTER_COMMIT 阶段原事务已经提交，但事务资源仍绑定在当前线程上。
 * 按 Spring {@link TransactionSynchronization#afterCommit()} 的约定，监听器如果以默认的
 * {@code REQUIRED} 传播写库，会加入一个已经提交的事务，写入<b>不会被再次提交而静默丢失</b>。
 * 需要写库的监听器必须声明 {@code @Transactional(propagation = Propagation.REQUIRES_NEW)}；
 * 需要可靠投递的场景应使用 outbox（chaos-mq-jdbc）在业务事务内落库。</p>
 *
 * <p><b>异常隔离：</b>AFTER_COMMIT 阶段事务已经提交，监听器抛出的异常如果传回调用方，
 * 调用方会误以为业务失败而重试，造成重复数据。因此提交后发布的监听器异常只记录日志、不向上抛出，
 * 并继续执行后续同步回调。无事务时立即发布，异常按原样抛出。</p>
 *
 * <p>设计决策：直接使用 Spring TransactionSynchronizationManager 而非引入额外抽象层，
 * 因为 chaos-service 本身已依赖 Spring TX，且该 API 是 Spring 生态中事务同步的标准做法。</p>
 */
public class TransactionalDomainEventPublisher implements DomainEventPublisher {

    private static final System.Logger LOG = System.getLogger(TransactionalDomainEventPublisher.class.getName());

    private final ApplicationEventPublisher applicationEventPublisher;

    public TransactionalDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(DomainEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishAfterCommit(event);
                }
            });
        } else {
            applicationEventPublisher.publishEvent(event);
        }
    }

    private void publishAfterCommit(DomainEvent event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (RuntimeException ex) {
            LOG.log(System.Logger.Level.ERROR,
                    "Domain event listener failed after transaction commit, event type: "
                            + event.getClass().getName() + "; the business transaction is already committed", ex);
        }
    }
}
