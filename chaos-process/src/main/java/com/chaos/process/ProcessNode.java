package com.chaos.process;

import java.util.Objects;

/**
 * 流程定义节点。
 *
 * @param key 定义内唯一节点编码
 * @param type 节点类型
 */
public record ProcessNode(String key, ProcessNodeType type) {

    /**
     * 创建节点并校验标识。
     */
    public ProcessNode {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Process node key must not be blank");
        }
        Objects.requireNonNull(type, "type must not be null");
    }
}
