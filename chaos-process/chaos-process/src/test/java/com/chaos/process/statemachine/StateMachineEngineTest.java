package com.chaos.process.statemachine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
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

    @Test
    void shouldRecordLowCardinalityTransitionOutcomes() {
        List<String> outcomes = new ArrayList<>();
        StateMachineEngine meteredEngine = new StateMachineEngine(
                (name, tags) -> outcomes.add(name + ":" + tags[1]));
        StateMachineDefinition<String, String, Void> definition =
                StateMachineDefinition.<String, String, Void>builder()
                        .transition("DRAFT", "SUBMIT", "PENDING")
                        .build();

        meteredEngine.fire(definition, "DRAFT", "SUBMIT", null);
        assertThrows(
                StateMachineException.class,
                () -> meteredEngine.fire(definition, "PENDING", "SUBMIT", null));
        StateMachineDefinition<String, String, Boolean> guarded =
                StateMachineDefinition.<String, String, Boolean>builder()
                        .transition("DRAFT", "SUBMIT", "PENDING", List.of(Boolean::booleanValue), null)
                        .build();
        assertThrows(StateMachineException.class, () -> meteredEngine.fire(guarded, "DRAFT", "SUBMIT", false));
        StateMachineDefinition<String, String, Void> failingAction =
                StateMachineDefinition.<String, String, Void>builder()
                        .transition("DRAFT", "SUBMIT", "PENDING", List.of(),
                                (source, event, target, context) -> {
                                    throw new IllegalStateException("action failed");
                                })
                        .build();
        assertThrows(IllegalStateException.class, () -> meteredEngine.fire(failingAction, "DRAFT", "SUBMIT", null));

        assertEquals(
                List.of(
                        "chaos.process.state.transitions:completed",
                        "chaos.process.state.transitions:invalid",
                        "chaos.process.state.transitions:guard-rejected",
                        "chaos.process.state.transitions:action-error"),
                outcomes);
    }
}
