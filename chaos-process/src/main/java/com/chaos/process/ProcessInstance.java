package com.chaos.process;

import java.time.Instant;

/**
 * 流程实例摘要。
 *
 * @param id 流程实例 ID
 * @param tenantId 租户标识
 * @param processCode 流程编码
 * @param definitionVersion 固定使用的流程定义版本
 * @param businessType 业务类型
 * @param businessId 业务主键
 * @param status 实例状态
 * @param startedAt 启动时间
 * @param completedAt 完成时间，可空
 */
public record ProcessInstance(
        String id,
        String tenantId,
        String processCode,
        int definitionVersion,
        String businessType,
        String businessId,
        ProcessInstanceStatus status,
        Instant startedAt,
        Instant completedAt) {
}
