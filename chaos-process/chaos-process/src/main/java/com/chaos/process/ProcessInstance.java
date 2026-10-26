package com.chaos.process;

import java.time.Instant;
import java.util.Map;

/**
 * 流程实例摘要。
 *
 * @param id 流程实例 ID
 * @param tenantId 租户标识
 * @param processCode 流程编码
 * @param definitionVersion 固定使用的流程定义版本
 * @param businessType 业务类型
 * @param businessId 业务主键
 * @param businessKey 面向业务人员的业务单号
 * @param variables 流程变量快照
 * @param startedBy 发起人
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
        String businessKey,
        Map<String, Object> variables,
        String startedBy,
        ProcessInstanceStatus status,
        Instant startedAt,
        Instant completedAt) {

    /**
     * 兼容 1.0 版本的流程实例构造方式。
     */
    public ProcessInstance(
            String id,
            String tenantId,
            String processCode,
            int definitionVersion,
            String businessType,
            String businessId,
            ProcessInstanceStatus status,
            Instant startedAt,
            Instant completedAt) {
        this(id, tenantId, processCode, definitionVersion, businessType, businessId, null, Map.of(), null,
                status, startedAt, completedAt);
    }

    /**
     * 创建不可变实例快照。
     */
    public ProcessInstance {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
