package com.chaos.process;

import java.util.Map;

/**
 * 启动业务流程实例的命令。
 *
 * @param tenantId 租户标识
 * @param processCode 流程编码
 * @param businessType 业务类型
 * @param businessId 业务主键
 * @param businessKey 面向业务人员的业务单号
 * @param variables 流程变量；不得放入密码、token 等敏感信息
 * @param requestId 调用幂等键
 * @param operatorId 发起人
 */
public record ProcessInstanceCommand(
        String tenantId,
        String processCode,
        String businessType,
        String businessId,
        String businessKey,
        Map<String, Object> variables,
        String requestId,
        String operatorId) {

    /**
     * 创建启动命令快照，防止流程变量在执行过程中被外部修改。
     */
    public ProcessInstanceCommand {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
