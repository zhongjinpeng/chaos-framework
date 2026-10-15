package com.chaos.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
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
}
