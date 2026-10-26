package com.chaos.process.statemachine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 已校验的状态机定义。
 *
 * <p>定义对象只描述状态、事件和转换，不包含数据库、Spring 或 HTTP 依赖。业务模块可以在启动期构造并缓存一份定义，
 * 运行期由 {@link StateMachineEngine} 执行。</p>
 *
 * @param <S> 状态类型
 * @param <E> 事件类型
 * @param <C> 状态转换上下文类型
 */
public final class StateMachineDefinition<S, E, C> {

    private final Map<Key<S, E>, StateMachineTransition<S, E, C>> transitions;

    private StateMachineDefinition(Map<Key<S, E>, StateMachineTransition<S, E, C>> transitions) {
        this.transitions = Map.copyOf(transitions);
    }

    /**
     * 返回当前状态和事件对应的转换规则。
     *
     * @param source 当前状态
     * @param event 触发事件
     * @return 匹配的转换规则；不存在时返回空
     */
    public StateMachineTransition<S, E, C> transition(S source, E event) {
        return transitions.get(new Key<>(source, event));
    }

    /**
     * 返回定义中的全部转换规则。
     *
     * @return 不可变转换规则列表
     */
    public List<StateMachineTransition<S, E, C>> transitions() {
        return List.copyOf(transitions.values());
    }

    /**
     * 创建状态机定义构造器。
     *
     * @param <S> 状态类型
     * @param <E> 事件类型
     * @param <C> 状态转换上下文类型
     * @return 构造器
     */
    public static <S, E, C> Builder<S, E, C> builder() {
        return new Builder<>();
    }

    private record Key<S, E>(S source, E event) {
    }

    /**
     * 状态机定义构造器。
     *
     * @param <S> 状态类型
     * @param <E> 事件类型
     * @param <C> 状态转换上下文类型
     */
    public static final class Builder<S, E, C> {

        private final Map<Key<S, E>, StateMachineTransition<S, E, C>> transitions = new HashMap<>();

        /**
         * 添加一条无额外前置条件的转换规则。
         *
         * @param source 转换前状态
         * @param event 触发事件
         * @param target 转换后状态
         * @return 当前构造器
         */
        public Builder<S, E, C> transition(S source, E event, S target) {
            return transition(source, event, target, List.of(), null);
        }

        /**
         * 添加一条带前置条件和动作的转换规则。
         *
         * @param source 转换前状态
         * @param event 触发事件
         * @param target 转换后状态
         * @param guards 前置条件；为空表示无额外条件
         * @param action 转换成功后的动作；为空表示无动作
         * @return 当前构造器
         * @throws IllegalArgumentException 同一个状态和事件已存在规则时抛出
         */
        public Builder<S, E, C> transition(
                S source,
                E event,
                S target,
                List<StateMachineGuard<C>> guards,
                StateMachineAction<S, E, C> action) {
            StateMachineTransition<S, E, C> transition =
                    new StateMachineTransition<>(source, event, target, guards, action);
            Key<S, E> key = new Key<>(source, event);
            if (transitions.putIfAbsent(key, transition) != null) {
                throw new IllegalArgumentException("Duplicate state transition: " + source + " + " + event);
            }
            return this;
        }

        /**
         * 构建不可变状态机定义。
         *
         * @return 状态机定义
         * @throws IllegalStateException 没有添加任何转换规则时抛出
         */
        public StateMachineDefinition<S, E, C> build() {
            if (transitions.isEmpty()) {
                throw new IllegalStateException("State machine must define at least one transition");
            }
            return new StateMachineDefinition<>(transitions);
        }
    }
}
