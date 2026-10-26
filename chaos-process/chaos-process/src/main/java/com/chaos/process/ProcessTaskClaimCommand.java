package com.chaos.process;

import java.util.Set;

/**
 * 领取流程任务的命令。
 *
 * @param tenantId 租户标识
 * @param taskId 任务 ID
 * @param operatorId 领取人
 * @param operatorRoleCodes 领取人当前角色编码
 * @param expectedVersion 调用方读取到的任务版本
 */
public record ProcessTaskClaimCommand(
        String tenantId,
        String taskId,
        String operatorId,
        Set<String> operatorRoleCodes,
        long expectedVersion) {

    /**
     * 创建不可变领取命令。
     */
    public ProcessTaskClaimCommand {
        operatorRoleCodes = operatorRoleCodes == null ? Set.of() : Set.copyOf(operatorRoleCodes);
    }
}
