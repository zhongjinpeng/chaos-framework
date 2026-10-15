package com.chaos.process;

/**
 * 状态转换前置条件。
 *
 * @param <C> 状态转换上下文类型
 */
@FunctionalInterface
public interface StateMachineGuard<C> {

    /**
     * 判断当前上下文是否允许状态转换。
     *
     * @param context 当前业务上下文
     * @return 条件满足时返回 {@code true}
     */
    boolean test(C context);
}
