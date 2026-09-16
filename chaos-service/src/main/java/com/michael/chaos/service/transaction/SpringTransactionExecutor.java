package com.michael.chaos.service.transaction;

import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基于 Spring TransactionTemplate 的事务执行器。
 */
public class SpringTransactionExecutor implements TransactionExecutor {

    private final TransactionTemplate transactionTemplate;

    /**
     * 创建 Spring 事务执行器。
     */
    public SpringTransactionExecutor(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 在 Spring 事务中执行有返回值的逻辑。
     */
    @Override
    public <T> T execute(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    /**
     * 在 Spring 事务中执行无返回值的逻辑。
     */
    @Override
    public void execute(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }
}
