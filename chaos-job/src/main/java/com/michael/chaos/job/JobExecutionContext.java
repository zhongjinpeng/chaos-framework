package com.michael.chaos.job;

import java.time.Instant;
import java.util.Map;

/**
 * 任务执行上下文。
 *
 * @param jobName 任务名称
 * @param executionId 本次执行 ID
 * @param parameters 任务参数
 * @param fireTime 触发时间
 */
public record JobExecutionContext(
        String jobName,
        String executionId,
        Map<String, String> parameters,
        Instant fireTime
) {

    /**
     * 规范化任务参数和触发时间。
     */
    public JobExecutionContext {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        fireTime = fireTime == null ? Instant.now() : fireTime;
    }
}
