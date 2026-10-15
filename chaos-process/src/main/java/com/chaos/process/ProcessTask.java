package com.chaos.process;

import java.time.Instant;
import java.util.Set;

/**
 * 流程用户任务摘要。
 *
 * @param id 任务 ID
 * @param processInstanceId 流程实例 ID
 * @param nodeKey 节点编码
 * @param tenantId 租户标识
 * @param businessType 业务类型
 * @param businessId 业务主键
 * @param status 任务状态
 * @param assigneeId 已指派用户，可空
 * @param candidateUserIds 候选用户
 * @param candidateRoleCodes 候选角色编码
 * @param claimExpiresAt 领取租约截止时间，可空
 * @param version 乐观锁版本
 */
public record ProcessTask(
        String id,
        String processInstanceId,
        String nodeKey,
        String tenantId,
        String businessType,
        String businessId,
        ProcessTaskStatus status,
        String assigneeId,
        Set<String> candidateUserIds,
        Set<String> candidateRoleCodes,
        Instant claimExpiresAt,
        long version) {

    /**
     * 创建不可变任务快照，避免候选人集合被调用方修改。
     */
    public ProcessTask {
        candidateUserIds = candidateUserIds == null ? Set.of() : Set.copyOf(candidateUserIds);
        candidateRoleCodes = candidateRoleCodes == null ? Set.of() : Set.copyOf(candidateRoleCodes);
        if (version < 0) {
            throw new IllegalArgumentException("Task version must not be negative");
        }
    }
}
