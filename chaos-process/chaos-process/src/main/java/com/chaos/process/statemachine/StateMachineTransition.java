package com.chaos.process.statemachine;

import java.util.List;
import java.util.Objects;

/**
 * 一条不可变状态转换规则。
 *
 * @param <S> 状态类型
 * @param <E> 事件类型
 * @param <C> 状态转换上下文类型
 * @param source 转换前状态
 * @param event 触发事件
 * @param target 转换后状态
 * @param guards 转换前置条件
 * @param action 转换成功后的动作
 */
public record StateMachineTransition<S, E, C>(
        S source,
        E event,
        S target,
        List<StateMachineGuard<C>> guards,
        StateMachineAction<S, E, C> action) {

    /**
     * 创建并校验状态转换规则。
     */
    public StateMachineTransition {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(target, "target must not be null");
        guards = guards == null ? List.of() : List.copyOf(guards);
        action = action == null ? (ignoredSource, ignoredEvent, ignoredTarget, ignoredContext) -> { } : action;
    }
}
