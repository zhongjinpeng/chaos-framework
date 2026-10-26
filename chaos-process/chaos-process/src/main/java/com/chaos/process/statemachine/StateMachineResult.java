package com.chaos.process.statemachine;

/**
 * 状态转换结果。
 *
 * @param <S> 状态类型
 * @param <E> 事件类型
 * @param source 转换前状态
 * @param event 触发事件
 * @param target 转换后状态
 */
public record StateMachineResult<S, E>(S source, E event, S target) {
}
