package com.chaos.process;

/**
 * 流程节点之间的连线。
 *
 * @param sourceNodeKey 来源节点编码
 * @param event 触发事件编码；开始节点可使用 {@code START}
 * @param targetNodeKey 目标节点编码
 */
public record ProcessTransition(String sourceNodeKey, String event, String targetNodeKey) {

    /**
     * 创建连线并校验必要标识。
     */
    public ProcessTransition {
        if (sourceNodeKey == null || sourceNodeKey.isBlank()) {
            throw new IllegalArgumentException("Source node key must not be blank");
        }
        if (event == null || event.isBlank()) {
            throw new IllegalArgumentException("Process event must not be blank");
        }
        if (targetNodeKey == null || targetNodeKey.isBlank()) {
            throw new IllegalArgumentException("Target node key must not be blank");
        }
    }
}
