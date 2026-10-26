package com.chaos.process;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * 流程定义节点。
 *
 * @param key 定义内唯一节点编码
 * @param type 节点类型
 * @param candidateUserIds 用户任务候选用户
 * @param candidateRoleCodes 用户任务候选角色
 * @param claimLease 用户任务领取租约；为空表示不自动过期
 */
public record ProcessNode(
        String key,
        ProcessNodeType type,
        Set<String> candidateUserIds,
        Set<String> candidateRoleCodes,
        Duration claimLease) {

    /**
     * 创建没有候选人限制和领取租约的节点。
     */
    public ProcessNode(String key, ProcessNodeType type) {
        this(key, type, Set.of(), Set.of(), null);
    }

    /**
     * 创建节点并校验标识。
     */
    public ProcessNode {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Process node key must not be blank");
        }
        Objects.requireNonNull(type, "type must not be null");
        candidateUserIds = candidateUserIds == null ? Set.of() : Set.copyOf(candidateUserIds);
        candidateRoleCodes = candidateRoleCodes == null ? Set.of() : Set.copyOf(candidateRoleCodes);
        if (type != ProcessNodeType.USER_TASK
                && (!candidateUserIds.isEmpty() || !candidateRoleCodes.isEmpty() || claimLease != null)) {
            throw new IllegalArgumentException("Task assignment can only be configured for USER_TASK nodes");
        }
        if (claimLease != null && (claimLease.isZero() || claimLease.isNegative())) {
            throw new IllegalArgumentException("Claim lease must be positive");
        }
    }
}
