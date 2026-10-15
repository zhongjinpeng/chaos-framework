package com.chaos.process;

/**
 * 状态转换成功后的业务动作。
 *
 * @param <S> 状态类型
 * @param <E> 事件类型
 * @param <C> 状态转换上下文类型
 */
@FunctionalInterface
public interface StateMachineAction<S, E, C> {

    /**
     * 执行状态转换后的动作。
     *
     * @param source 转换前状态
     * @param event 触发事件
     * @param target 转换后状态
     * @param context 当前业务上下文
     */
    void execute(S source, E event, S target, C context);
}
