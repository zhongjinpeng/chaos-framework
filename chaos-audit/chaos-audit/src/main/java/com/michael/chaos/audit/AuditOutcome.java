package com.michael.chaos.audit;

/**
 * 审计事件结果。
 */
public enum AuditOutcome {

    /**
     * 操作成功。
     */
    SUCCESS,

    /**
     * 操作失败。
     */
    FAILURE,

    /**
     * 操作被拒绝。
     */
    DENIED
}
