package com.chaos.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProcessDefinitionTest {

    @Test
    void shouldCreateImmutableProcessDefinitionSnapshot() {
        ProcessDefinition definition = new ProcessDefinition(
                "ENTRUSTMENT_REVIEW",
                1,
                List.of(
                        new ProcessNode("start", ProcessNodeType.START),
                        new ProcessNode("review", ProcessNodeType.USER_TASK),
                        new ProcessNode("end", ProcessNodeType.END)),
                List.of(
                        new ProcessTransition("start", "START", "review"),
                        new ProcessTransition("review", "APPROVE", "end")));

        assertEquals("ENTRUSTMENT_REVIEW", definition.code());
        assertEquals(null, definition.tenantId());
        assertEquals(1, definition.version());
        assertThrows(UnsupportedOperationException.class,
                () -> definition.nodes().add(new ProcessNode("other", ProcessNodeType.END)));
    }

    @Test
    void shouldRequireStartAndEndNodes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessDefinition(
                        "INVALID",
                        1,
                        List.of(new ProcessNode("review", ProcessNodeType.USER_TASK)),
                        List.of()));
    }

    @Test
    void shouldRequireExplicitNullForSharedDefinition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessDefinition(
                        " ",
                        "SHARED",
                        1,
                        List.of(
                                new ProcessNode("start", ProcessNodeType.START),
                                new ProcessNode("end", ProcessNodeType.END)),
                        List.of(new ProcessTransition("start", "START", "end"))));
    }

    @Test
    void shouldRejectDuplicateNodesAndDanglingTransitions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessDefinition(
                        "DUPLICATE_NODE",
                        1,
                        List.of(
                                new ProcessNode("start", ProcessNodeType.START),
                                new ProcessNode("start", ProcessNodeType.END)),
                        List.of()));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessDefinition(
                        "DANGLING_TRANSITION",
                        1,
                        List.of(
                                new ProcessNode("start", ProcessNodeType.START),
                                new ProcessNode("end", ProcessNodeType.END)),
                        List.of(new ProcessTransition("start", "START", "missing"))));
    }

    @Test
    void shouldRejectAmbiguousOrDisconnectedGraphs() {
        List<ProcessNode> nodes = List.of(
                new ProcessNode("start", ProcessNodeType.START),
                new ProcessNode("review", ProcessNodeType.USER_TASK),
                new ProcessNode("end", ProcessNodeType.END));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessDefinition(
                        "AMBIGUOUS",
                        1,
                        nodes,
                        List.of(
                                new ProcessTransition("start", "START", "review"),
                                new ProcessTransition("review", "APPROVE", "end"),
                                new ProcessTransition("review", "APPROVE", "start"))));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessDefinition(
                        "DISCONNECTED",
                        1,
                        nodes,
                        List.of(new ProcessTransition("start", "START", "end"))));
    }

    @Test
    void shouldOnlyAllowTaskAssignmentOnUserTasks() {
        ProcessNode review = new ProcessNode(
                "review",
                ProcessNodeType.USER_TASK,
                Set.of("user-1"),
                Set.of("reviewer"),
                Duration.ofMinutes(5));

        assertEquals(Set.of("reviewer"), review.candidateRoleCodes());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessNode(
                        "service",
                        ProcessNodeType.SERVICE_TASK,
                        Set.of("user-1"),
                        Set.of(),
                        null));
    }
}
