package com.chaos.process.statemachine;

import com.chaos.core.exception.ChaosException;

/**
 * 状态机执行失败异常。
 */
public final class StateMachineException extends ChaosException {

    /**
     * 使用稳定错误码和业务上下文创建异常。
     *
     * @param errorCode 状态机错误码
     * @param message 包含状态和事件信息的诊断消息
     */
    public StateMachineException(StateMachineErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
