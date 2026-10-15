
package com.chaos.process;

/**
 * 流程引擎端口。
 *
 * <p>接口只描述流程用例，不规定持久化技术。JDBC、Redis 或消息实现应在上层模块提供，且必须在事务中完成任务状态的条件更新。</p>
 */
public interface ProcessEngine {

    /**
     * 启动一个业务流程实例。
     *
     * @param command 启动命令，包含租户、业务标识和幂等键
     * @return 已启动或幂等命中的流程实例
     */
    ProcessInstance start(ProcessInstanceCommand command);

    /**
     * 领取一个待办任务。
     *
     * @param taskId 任务 ID
     * @param operatorId 领取人
     * @param expectedVersion 调用方读取到的任务版本
     * @return 领取后的任务
     */
    ProcessTask claim(String taskId, String operatorId, long expectedVersion);

    /**
     * 完成一个待办任务并驱动后续节点。
     *
     * @param taskId 任务 ID
     * @param event 节点事件，例如 {@code APPROVE} 或 {@code REJECT}
     * @param operatorId 操作人
     * @param comment 处理意见，可空
     * @param expectedVersion 调用方读取到的任务版本
     * @param requestId 调用幂等键
     * @return 完成后的任务
     */
    ProcessTask complete(
            String taskId,
            String event,
            String operatorId,
            String comment,
            long expectedVersion,
            String requestId);
}
