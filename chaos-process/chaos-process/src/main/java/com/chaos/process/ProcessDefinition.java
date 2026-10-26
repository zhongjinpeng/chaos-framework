package com.chaos.process;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已发布流程定义的不可变快照。
 *
 * @param tenantId 租户标识；为空表示所有租户可用的共享定义
 * @param code 流程稳定编码
 * @param version 已发布版本号；运行实例必须固定版本
 * @param nodes 节点列表
 * @param transitions 节点连线列表
 */
public record ProcessDefinition(
        String tenantId,
        String code,
        int version,
        List<ProcessNode> nodes,
        List<ProcessTransition> transitions) {

    /**
     * 创建所有租户共享的流程定义。
     */
    public ProcessDefinition(
            String code,
            int version,
            List<ProcessNode> nodes,
            List<ProcessTransition> transitions) {
        this(null, code, version, nodes, transitions);
    }

    /**
     * 创建流程定义快照并校验基础结构。
     */
    public ProcessDefinition {
        if (tenantId != null && tenantId.isBlank()) {
            throw new IllegalArgumentException("Process tenantId must not be blank; use null for a shared definition");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Process code must not be blank");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("Process version must be positive");
        }
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        Map<String, ProcessNode> nodesByKey = new HashMap<>();
        for (ProcessNode node : nodes) {
            if (nodesByKey.putIfAbsent(node.key(), node) != null) {
                throw new IllegalArgumentException("Duplicate process node key: " + node.key());
            }
        }
        List<ProcessNode> startNodes = nodes.stream()
                .filter(node -> node.type() == ProcessNodeType.START)
                .toList();
        if (startNodes.size() != 1) {
            throw new IllegalArgumentException("Process must define exactly one START node");
        }
        if (nodes.stream().filter(node -> node.type() == ProcessNodeType.END).count() == 0) {
            throw new IllegalArgumentException("Process must define at least one END node");
        }
        Set<TransitionKey> transitionKeys = new HashSet<>();
        Map<String, List<String>> targetsBySource = new HashMap<>();
        Map<String, List<String>> sourcesByTarget = new HashMap<>();
        for (ProcessTransition transition : transitions) {
            ProcessNode source = nodesByKey.get(transition.sourceNodeKey());
            if (source == null) {
                throw new IllegalArgumentException(
                        "Process transition source node does not exist: " + transition.sourceNodeKey());
            }
            ProcessNode target = nodesByKey.get(transition.targetNodeKey());
            if (target == null) {
                throw new IllegalArgumentException(
                        "Process transition target node does not exist: " + transition.targetNodeKey());
            }
            TransitionKey transitionKey = new TransitionKey(transition.sourceNodeKey(), transition.event());
            if (!transitionKeys.add(transitionKey)) {
                throw new IllegalArgumentException(
                        "Duplicate process transition: " + transition.sourceNodeKey() + " + " + transition.event());
            }
            if (source.type() == ProcessNodeType.END) {
                throw new IllegalArgumentException("END node must not define outgoing transitions: " + source.key());
            }
            if (target.type() == ProcessNodeType.START) {
                throw new IllegalArgumentException("START node must not define incoming transitions: " + target.key());
            }
            targetsBySource.computeIfAbsent(source.key(), ignored -> new ArrayList<>()).add(target.key());
            sourcesByTarget.computeIfAbsent(target.key(), ignored -> new ArrayList<>()).add(source.key());
        }
        ProcessNode startNode = startNodes.getFirst();
        List<ProcessTransition> startTransitions = transitions.stream()
                .filter(transition -> transition.sourceNodeKey().equals(startNode.key()))
                .toList();
        if (startTransitions.size() != 1 || !"START".equals(startTransitions.getFirst().event())) {
            throw new IllegalArgumentException("START node must define exactly one START transition");
        }
        requireAllNodesReachable(nodesByKey.keySet(), startNode.key(), targetsBySource);
        requireAllNodesCanReachEnd(nodes, sourcesByTarget);
    }

    private static void requireAllNodesReachable(
            Set<String> nodeKeys,
            String startNodeKey,
            Map<String, List<String>> targetsBySource) {
        Set<String> reachable = traverse(Set.of(startNodeKey), targetsBySource);
        if (!reachable.containsAll(nodeKeys)) {
            Set<String> unreachable = new HashSet<>(nodeKeys);
            unreachable.removeAll(reachable);
            throw new IllegalArgumentException("Process contains unreachable nodes: " + unreachable);
        }
    }

    private static void requireAllNodesCanReachEnd(
            List<ProcessNode> nodes,
            Map<String, List<String>> sourcesByTarget) {
        Set<String> endNodeKeys = nodes.stream()
                .filter(node -> node.type() == ProcessNodeType.END)
                .map(ProcessNode::key)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> canReachEnd = traverse(endNodeKeys, sourcesByTarget);
        Set<String> deadEnds = nodes.stream()
                .map(ProcessNode::key)
                .filter(nodeKey -> !canReachEnd.contains(nodeKey))
                .collect(java.util.stream.Collectors.toSet());
        if (!deadEnds.isEmpty()) {
            throw new IllegalArgumentException("Process nodes cannot reach an END node: " + deadEnds);
        }
    }

    private static Set<String> traverse(Set<String> initialNodes, Map<String, List<String>> adjacentNodes) {
        Set<String> visited = new HashSet<>(initialNodes);
        ArrayDeque<String> pending = new ArrayDeque<>(initialNodes);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            for (String adjacent : adjacentNodes.getOrDefault(current, List.of())) {
                if (visited.add(adjacent)) {
                    pending.addLast(adjacent);
                }
            }
        }
        return visited;
    }

    private record TransitionKey(String sourceNodeKey, String event) {
    }
}
