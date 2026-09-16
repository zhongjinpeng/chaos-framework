package com.michael.chaos.service.transaction;

import java.util.function.Supplier;

/**
 * 事务执行器。
 *
 * <p>应用服务需要显式事务边界时依赖该抽象，避免直接依赖 Spring 事务模板。</p>
 */
public interface TransactionExecutor {

    /**
     * 在事务中执行有返回值的逻辑。
     *
     * @param action 业务逻辑
     * @param <T> 返回值类型
     * @return 业务逻辑返回值
     */
    <T> T execute(Supplier<T> action);

    /**
     * 在事务中执行无返回值的逻辑。
     *
     * @param action 业务逻辑
     */
    void execute(Runnable action);
}
