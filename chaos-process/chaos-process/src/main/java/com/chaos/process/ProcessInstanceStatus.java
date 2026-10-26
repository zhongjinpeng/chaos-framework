package com.chaos.process;

/**
 * 流程实例状态。
 */
public enum ProcessInstanceStatus {

    /** 实例正在执行。 */
    RUNNING,
    /** 实例正常完成。 */
    COMPLETED,
    /** 实例被业务取消。 */
    CANCELLED,
    /** 实例因不可恢复错误暂停，等待人工处理。 */
    SUSPENDED
}
