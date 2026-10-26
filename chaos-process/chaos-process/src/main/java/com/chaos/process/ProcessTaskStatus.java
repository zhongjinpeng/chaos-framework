package com.chaos.process;

/**
 * 用户待办任务状态。
 */
public enum ProcessTaskStatus {

    /** 可被候选人领取。 */
    READY,
    /** 已被用户领取，仍未完成。 */
    CLAIMED,
    /** 已完成。 */
    COMPLETED,
    /** 被流程驳回或取消。 */
    CANCELLED,
    /** 超过租约或截止时间。 */
    EXPIRED
}
