package com.michael.chaos.job;

/**
 * 由定时任务处理器实现的任务执行端口。
 */
public interface JobExecutor {

    /**
     * 执行一次任务触发事件。
     */
    void execute(JobExecutionContext context);
}
