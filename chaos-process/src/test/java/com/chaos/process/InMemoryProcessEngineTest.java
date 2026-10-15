package com.chaos.process;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryProcessEngineTest {

    @Test
    void shouldStartClaimAndCompleteUserTask() {
        InMemoryProcessEngine engine = new InMemoryProcessEngine();
        engine.register(new ProcessDefinition(
                "ENTRUSTMENT_REVIEW",
                1,
                List.of(
                        new ProcessNode("start", ProcessNodeType.START),
                        new ProcessNode("review", ProcessNodeType.USER_TASK),
                        new ProcessNode("end", ProcessNodeType.END)),
                List.of(
                        new ProcessTransition("start", "START", "review"),
                        new ProcessTransition("review", "APPROVE", "end"))));

        ProcessInstance instance = engine.start(new ProcessInstanceCommand(
                "tenant-a", "ENTRUSTMENT_REVIEW", "ENTRUSTMENT", "order-1", "WT-1",
                Map.of(), "request-1", "creator"));
        var ready = engine.taskFor(instance.id());
        assertEquals(ProcessTaskStatus.READY, ready.status());
        var claimed = engine.claim(ready.id(), "reviewer", ready.version());
        var completed = engine.complete(
                claimed.id(), "APPROVE", "reviewer", "通过", claimed.version(), "request-2");
        assertEquals(ProcessTaskStatus.COMPLETED, completed.status());
        assertEquals(completed.processInstanceId(), instance.id());
    }
}
