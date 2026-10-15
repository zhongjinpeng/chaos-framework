package com.chaos.process;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * 已发布流程定义的不可变快照。
 *
 * @param code 流程稳定编码
 * @param version 已发布版本号；运行实例必须固定版本
 * @param nodes 节点列表
 * @param transitions 节点连线列表
 */
public record ProcessDefinition(
        String code,
        int version,
        List<ProcessNode> nodes,
        List<ProcessTransition> transitions) {

    /**
     * 创建流程定义快照并校验基础结构。
     */
    public ProcessDefinition {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Process code must not be blank");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("Process version must be positive");
        }
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        Set<String> nodeKeys = new HashSet<>();
        for (ProcessNode node : nodes) {
            if (!nodeKeys.add(node.key())) {
                throw new IllegalArgumentException("Duplicate process node key: " + node.key());
            }
        }
        if (nodes.stream().filter(node -> node.type() == ProcessNodeType.START).count() != 1) {
            throw new IllegalArgumentException("Process must define exactly one START node");
        }
        if (nodes.stream().filter(node -> node.type() == ProcessNodeType.END).count() == 0) {
            throw new IllegalArgumentException("Process must define at least one END node");
        }
        for (ProcessTransition transition : transitions) {
            if (!nodeKeys.contains(transition.sourceNodeKey())) {
                throw new IllegalArgumentException(
                        "Process transition source node does not exist: " + transition.sourceNodeKey());
            }
            if (!nodeKeys.contains(transition.targetNodeKey())) {
                throw new IllegalArgumentException(
                        "Process transition target node does not exist: " + transition.targetNodeKey());
            }
        }
    }
}
