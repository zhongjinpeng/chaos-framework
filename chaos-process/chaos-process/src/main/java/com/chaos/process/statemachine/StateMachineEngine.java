package com.chaos.process.statemachine;

import com.chaos.core.metrics.ChaosMeterNames;
import com.chaos.core.metrics.ChaosMetrics;
import com.chaos.core.metrics.NoopChaosMetrics;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 同步执行状态转换的无状态引擎。
 *
 * <p>引擎只负责校验转换和执行领域动作，不负责保存当前状态。持久化状态应由应用服务在事务中以期望状态或版本条件更新，
 * 这样数据库并发控制不会被内存锁替代。</p>
 */
public final class StateMachineEngine {

    private static final Logger log = LoggerFactory.getLogger(StateMachineEngine.class);

    private final ChaosMetrics metrics;

    /**
     * 创建不采集指标的状态机引擎。
     */
    public StateMachineEngine() {
        this(NoopChaosMetrics.instance());
    }

    /**
     * 创建使用指定治理指标端口的状态机引擎。
     *
     * @param metrics 指标上报端口；为空时使用空实现
     */
    public StateMachineEngine(ChaosMetrics metrics) {
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
    }

    /**
     * 执行一次状态转换。
     *
     * @param definition 状态机定义
     * @param source 当前状态
     * @param event 触发事件
     * @param context 业务上下文
     * @param <S> 状态类型
     * @param <E> 事件类型
     * @param <C> 状态转换上下文类型
     * @return 转换结果
     * @throws StateMachineException 未配置转换或前置条件失败时抛出
     */
    public <S, E, C> StateMachineResult<S, E> fire(
            StateMachineDefinition<S, E, C> definition,
            S source,
            E event,
            C context) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(event, "event must not be null");
        log.debug("Firing state transition, source={}, event={}", source, event);
        StateMachineTransition<S, E, C> transition = definition.transition(source, event);
        if (transition == null) {
            record("invalid");
            log.debug("State transition rejected because no rule exists, source={}, event={}", source, event);
            throw new StateMachineException(
                    StateMachineErrorCode.INVALID_TRANSITION,
                    "No transition configured for state " + source + " and event " + event);
        }
        for (StateMachineGuard<C> guard : transition.guards()) {
            if (!guard.test(context)) {
                record("guard-rejected");
                log.debug("State transition rejected by guard, source={}, event={}, target={}",
                        source, event, transition.target());
                throw new StateMachineException(
                        StateMachineErrorCode.GUARD_REJECTED,
                        "Transition guard rejected state " + source + " and event " + event);
            }
        }
        try {
            transition.action().execute(source, event, transition.target(), context);
        } catch (RuntimeException exception) {
            record("action-error");
            throw exception;
        }
        record("completed");
        log.debug("State transition completed, source={}, event={}, target={}",
                source, event, transition.target());
        return new StateMachineResult<>(source, event, transition.target());
    }

    private void record(String outcome) {
        metrics.increment(ChaosMeterNames.PROCESS_STATE_TRANSITIONS, ChaosMeterNames.TAG_OUTCOME, outcome);
    }
}
