package com.chaos.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class StateMachineEngineTest {

    private final StateMachineEngine engine = new StateMachineEngine();

    @Test
    void shouldFireTransitionAndRunAction() {
        AtomicBoolean actionCalled = new AtomicBoolean();
        StateMachineDefinition<String, String, String> definition = StateMachineDefinition.<String, String, String>builder()
                .transition("DRAFT", "SUBMIT", "PENDING", List.of(context -> context.equals("complete")),
                        (source, event, target, context) -> actionCalled.set(true))
                .build();

        StateMachineResult<String, String> result = engine.fire(definition, "DRAFT", "SUBMIT", "complete");

        assertEquals("DRAFT", result.source());
        assertEquals("PENDING", result.target());
        assertEquals("SUBMIT", result.event());
        assertEquals(true, actionCalled.get());
    }

    @Test
    void shouldRejectUnconfiguredTransition() {
        StateMachineDefinition<String, String, Void> definition = StateMachineDefinition.<String, String, Void>builder()
                .transition("DRAFT", "SUBMIT", "PENDING")
                .build();

        StateMachineException exception = assertThrows(
                StateMachineException.class,
                () -> engine.fire(definition, "PENDING", "SUBMIT", null));

        assertEquals(StateMachineErrorCode.INVALID_TRANSITION, exception.errorCode());
    }

    @Test
    void shouldRejectWhenGuardFailsWithoutRunningAction() {
        AtomicBoolean actionCalled = new AtomicBoolean();
        StateMachineDefinition<String, String, Boolean> definition = StateMachineDefinition.<String, String, Boolean>builder()
                .transition("DRAFT", "SUBMIT", "PENDING", List.of(Boolean::booleanValue),
                        (source, event, target, context) -> actionCalled.set(true))
                .build();

        StateMachineException exception = assertThrows(
                StateMachineException.class,
                () -> engine.fire(definition, "DRAFT", "SUBMIT", false));

        assertEquals(StateMachineErrorCode.GUARD_REJECTED, exception.errorCode());
        assertEquals(false, actionCalled.get());
    }

    @Test
    void shouldRejectDuplicateTransitionAtDefinitionBuildTime() {
        StateMachineDefinition.Builder<String, String, Void> builder =
                StateMachineDefinition.<String, String, Void>builder()
                        .transition("DRAFT", "SUBMIT", "PENDING");

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.transition("DRAFT", "SUBMIT", "REJECTED"));
    }
}
