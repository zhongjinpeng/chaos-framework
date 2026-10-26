package com.chaos.process;

/**
 * 完成流程任务的命令。
 *
 * @param tenantId 租户标识
 * @param taskId 任务 ID
 * @param event 节点事件
 * @param operatorId 操作人
 * @param comment 处理意见，可空
 * @param expectedVersion 调用方读取到的任务版本
 * @param requestId 调用幂等键
 */
public record ProcessTaskCompletionCommand(
        String tenantId,
        String taskId,
        String event,
        String operatorId,
        String comment,
        long expectedVersion,
        String requestId) {
}
