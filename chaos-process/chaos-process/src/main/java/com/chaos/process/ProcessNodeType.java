package com.chaos.process;

/**
 * 流程定义节点类型。
 */
public enum ProcessNodeType {

    /** 流程开始节点。 */
    START,
    /** 需要用户处理的待办节点。 */
    USER_TASK,
    /** 由应用服务执行的业务动作节点。 */
    SERVICE_TASK,
    /** 根据变量选择一条后续路径的条件节点。 */
    EXCLUSIVE_GATEWAY,
    /** 流程结束节点。 */
    END
}
