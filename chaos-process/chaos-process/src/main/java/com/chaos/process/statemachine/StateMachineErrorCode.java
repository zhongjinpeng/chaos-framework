package com.chaos.process.statemachine;

import com.chaos.core.exception.ErrorCode;

/**
 * 通用状态机错误码。
 */
public enum StateMachineErrorCode implements ErrorCode {

    /** 当前状态没有配置目标事件。 */
    INVALID_TRANSITION("当前状态不允许执行该事件"),
    /** 状态转换的业务前置条件未满足。 */
    GUARD_REJECTED("状态转换前置条件未满足");

    private final String message;

    StateMachineErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String message() {
        return message;
    }
}
