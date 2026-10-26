
package com.chaos.process;

/**
 * 流程引擎端口。
 *
 * <p>接口只描述流程用例，不规定持久化技术。JDBC、Redis 或消息实现应在上层模块提供，且必须在事务中完成任务状态的条件更新。</p>
 */
public interface ProcessEngine {

    /**
     * 发布一个不可变流程定义。持久化实现应以租户、编码和版本建立唯一约束。
     */
    default void register(ProcessDefinition definition) {
        throw new UnsupportedOperationException("This process engine does not support definition registration");
    }

    /**
     * 查询流程实例当前活动任务。
     */
    default ProcessTask taskFor(String processInstanceId) {
        throw new UnsupportedOperationException("This process engine does not support task queries");
    }

    /**
     * 启动一个业务流程实例。
     *
     * @param command 启动命令，包含租户、业务标识和幂等键
     * @return 已启动或幂等命中的流程实例
     */
    ProcessInstance start(ProcessInstanceCommand command);

    /**
     * 使用显式租户和角色上下文领取待办任务。
     *
     * <p>生产实现应在同一个条件更新中校验租户、候选人、租约和版本。</p>
     *
     * @param command 领取命令
     * @return 领取后的任务
     */
    ProcessTask claim(ProcessTaskClaimCommand command);

    /**
     * 使用显式租户上下文完成待办任务。
     *
     * <p>生产实现应在同一事务内完成租户校验、幂等校验、条件更新和后续任务创建。</p>
     *
     * @param command 完成命令
     * @return 完成后的任务
     */
    ProcessTask complete(ProcessTaskCompletionCommand command);

}
