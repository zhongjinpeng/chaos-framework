package com.chaos.process.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.chaos.process.ProcessDefinition;
import com.chaos.process.ProcessInstance;
import com.chaos.process.ProcessInstanceCommand;
import com.chaos.process.ProcessNode;
import com.chaos.process.ProcessNodeType;
import com.chaos.process.ProcessTaskClaimCommand;
import com.chaos.process.ProcessTaskCompletionCommand;
import com.chaos.process.ProcessTask;
import com.chaos.process.ProcessTaskStatus;
import com.chaos.process.ProcessTransition;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
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
        var claimed = claim(engine, ready, "reviewer");
        var completed = complete(engine, claimed, "APPROVE", "reviewer", "通过", "request-2");
        assertEquals(ProcessTaskStatus.COMPLETED, completed.status());
        assertEquals(completed.processInstanceId(), instance.id());
        assertEquals("APPROVE", completed.completionEvent());
        assertEquals("通过", completed.comment());
        assertEquals("WT-1", instance.businessKey());
        assertEquals("creator", instance.startedBy());
    }

    @Test
    void shouldReplayTaskCompletionForSameRequest() {
        InMemoryProcessEngine engine = engineWithUserTaskEndingProcess();
        ProcessInstance instance = engine.start(command("request-start"));
        var ready = engine.taskFor(instance.id());
        var claimed = claim(engine, ready, "reviewer");

        var completed = complete(engine, claimed, "APPROVE", "reviewer", "approved", "request-complete");
        var replayed = complete(engine, claimed, "APPROVE", "reviewer", "approved", "request-complete");

        assertEquals(completed, replayed);
    }

    @Test
    void shouldRejectRequestIdReusedByAnotherOperation() {
        InMemoryProcessEngine engine = engineWithUserTaskEndingProcess();
        ProcessInstance instance = engine.start(command("shared-request"));
        var ready = engine.taskFor(instance.id());
        var claimed = claim(engine, ready, "reviewer");

        assertThrows(
                IllegalArgumentException.class,
                () -> complete(engine, claimed, "APPROVE", "reviewer", "approved", "shared-request"));
        assertEquals(claimed, engine.taskFor(instance.id()));
    }

    @Test
    void shouldNotCreatePartialInstanceWhenInitialNodeIsUnsupported() {
        InMemoryProcessEngine engine = new InMemoryProcessEngine();
        engine.register(new ProcessDefinition(
                "SERVICE_PROCESS",
                1,
                List.of(
                        new ProcessNode("start", ProcessNodeType.START),
                        new ProcessNode("service", ProcessNodeType.SERVICE_TASK),
                        new ProcessNode("end", ProcessNodeType.END)),
                List.of(
                        new ProcessTransition("start", "START", "service"),
                        new ProcessTransition("service", "DONE", "end"))));
        ProcessInstanceCommand command = new ProcessInstanceCommand(
                "tenant-a", "SERVICE_PROCESS", "ENTRUSTMENT", "order-1", "WT-1",
                Map.of(), "request-start", "creator");

        assertThrows(IllegalStateException.class, () -> engine.start(command));
        assertThrows(IllegalStateException.class, () -> engine.start(command));
    }

    @Test
    void shouldNotCompleteTaskWhenNextNodeIsUnsupported() {
        InMemoryProcessEngine engine = new InMemoryProcessEngine();
        engine.register(new ProcessDefinition(
                "SERVICE_PROCESS",
                1,
                List.of(
                        new ProcessNode("start", ProcessNodeType.START),
                        new ProcessNode("review", ProcessNodeType.USER_TASK),
                        new ProcessNode("service", ProcessNodeType.SERVICE_TASK),
                        new ProcessNode("end", ProcessNodeType.END)),
                List.of(
                        new ProcessTransition("start", "START", "review"),
                        new ProcessTransition("review", "APPROVE", "service"),
                        new ProcessTransition("service", "DONE", "end"))));
        ProcessInstance instance = engine.start(command("SERVICE_PROCESS", "request-start"));
        var ready = engine.taskFor(instance.id());
        var claimed = claim(engine, ready, "reviewer");

        assertThrows(
                IllegalStateException.class,
                () -> complete(engine, claimed, "APPROVE", "reviewer", "approved", "request-complete"));
        assertEquals(claimed, engine.taskFor(instance.id()));
    }

    @Test
    void shouldRecordProcessOperationOutcomes() {
        List<String> metrics = new ArrayList<>();
        InMemoryProcessEngine engine = new InMemoryProcessEngine(
                Clock.systemUTC(),
                (name, tags) -> metrics.add(name + ":" + tags[1]));
        registerUserTaskEndingProcess(engine);
        ProcessInstanceCommand command = command("request-start");

        ProcessInstance instance = engine.start(command);
        engine.start(command);
        var ready = engine.taskFor(instance.id());
        var claimed = claim(engine, ready, "reviewer");
        complete(engine, claimed, "APPROVE", "reviewer", "approved", "request-complete");

        assertEquals(
                List.of(
                        "chaos.process.instances.started:started",
                        "chaos.process.instances.started:replayed",
                        "chaos.process.tasks.claimed:claimed",
                        "chaos.process.tasks.completed:completed"),
                metrics);
    }

    @Test
    void shouldRecordConflictForbiddenAndReplayOutcomes() {
        List<String> metrics = new ArrayList<>();
        InMemoryProcessEngine engine = new InMemoryProcessEngine(
                Clock.systemUTC(),
                (name, tags) -> metrics.add(name + ":" + tags[1]));
        registerAssignedProcess(engine);
        ProcessInstance instance = engine.start(command("request-start"));
        engine.start(command("request-start"));
        assertThrows(IllegalStateException.class, () -> engine.start(command("request-other")));
        var ready = engine.taskFor(instance.id());
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.claim(new ProcessTaskClaimCommand(
                        "tenant-a", ready.id(), "guest", Set.of("guest"), ready.version())));
        var claimed = engine.claim(new ProcessTaskClaimCommand(
                "tenant-a", ready.id(), "reviewer", Set.of("reviewer"), ready.version()));
        assertThrows(
                IllegalStateException.class,
                () -> engine.claim(new ProcessTaskClaimCommand(
                        "tenant-a", ready.id(), "reviewer-2", Set.of("reviewer"), claimed.version())));
        ProcessTaskCompletionCommand completion = new ProcessTaskCompletionCommand(
                "tenant-a", claimed.id(), "APPROVE", "reviewer", "approved",
                claimed.version(), "request-complete");
        assertThrows(
                IllegalStateException.class,
                () -> engine.complete(new ProcessTaskCompletionCommand(
                        "tenant-a", claimed.id(), "APPROVE", "other", "approved",
                        claimed.version(), "request-conflict")));
        engine.complete(completion);
        engine.complete(completion);

        assertEquals(
                List.of(
                        "chaos.process.instances.started:started",
                        "chaos.process.instances.started:replayed",
                        "chaos.process.instances.started:conflict",
                        "chaos.process.tasks.claimed:forbidden",
                        "chaos.process.tasks.claimed:claimed",
                        "chaos.process.tasks.claimed:conflict",
                        "chaos.process.tasks.completed:conflict",
                        "chaos.process.tasks.completed:completed",
                        "chaos.process.tasks.completed:replayed"),
                metrics);
    }

    @Test
    void shouldKeepTenantAndRequestIdAsSeparateIdempotencyKeyParts() {
        InMemoryProcessEngine engine = engineWithUserTaskEndingProcess();
        ProcessInstance first = engine.start(command("tenant:a", "order-1", "request"));
        ProcessInstance second = engine.start(command("tenant", "order-2", "a:request"));

        assertEquals("tenant:a", first.tenantId());
        assertEquals("tenant", second.tenantId());
    }

    @Test
    void shouldRejectRequestIdReusedWithDifferentCommand() {
        InMemoryProcessEngine engine = engineWithUserTaskEndingProcess();
        engine.start(command("tenant-a", "order-1", "request-start"));

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.start(command("tenant-a", "order-2", "request-start")));
    }

    @Test
    void shouldRejectCompletionReplayWithDifferentPayload() {
        InMemoryProcessEngine engine = engineWithUserTaskEndingProcess();
        ProcessInstance instance = engine.start(command("request-start"));
        var ready = engine.taskFor(instance.id());
        var claimed = claim(engine, ready, "reviewer");
        complete(engine, claimed, "APPROVE", "reviewer", "approved", "request-complete");

        assertThrows(
                IllegalArgumentException.class,
                () -> complete(engine, claimed, "APPROVE", "reviewer", "changed", "request-complete"));
    }

    @Test
    void shouldEnforceTenantCandidatesAndClaimLease() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-15T12:00:00Z"));
        InMemoryProcessEngine engine = new InMemoryProcessEngine(clock);
        registerAssignedProcess(engine);
        ProcessInstance instance = engine.start(command("request-start"));
        var ready = engine.taskFor(instance.id());

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.claim(new ProcessTaskClaimCommand(
                        "tenant-b", ready.id(), "reviewer-1", Set.of("reviewer"), ready.version())));
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.claim(new ProcessTaskClaimCommand(
                        "tenant-a", ready.id(), "reviewer-1", Set.of("guest"), ready.version())));

        var firstClaim = engine.claim(new ProcessTaskClaimCommand(
                "tenant-a", ready.id(), "reviewer-1", Set.of("reviewer"), ready.version()));
        assertEquals(Instant.parse("2026-10-15T12:05:00Z"), firstClaim.claimExpiresAt());

        clock.advance(Duration.ofMinutes(6));
        var secondClaim = engine.claim(new ProcessTaskClaimCommand(
                "tenant-a", ready.id(), "reviewer-2", Set.of("reviewer"), firstClaim.version()));
        assertEquals("reviewer-2", secondClaim.assigneeId());
        assertEquals(2, secondClaim.version());

        assertThrows(
                IllegalStateException.class,
                () -> engine.complete(new ProcessTaskCompletionCommand(
                        "tenant-a", ready.id(), "APPROVE", "reviewer-1", "stale",
                        secondClaim.version(), "request-stale")));
        var completed = engine.complete(new ProcessTaskCompletionCommand(
                "tenant-a", ready.id(), "APPROVE", "reviewer-2", "approved",
                secondClaim.version(), "request-complete"));
        assertEquals("approved", completed.comment());
    }

    @Test
    void shouldRejectSecondActiveInstanceForSameBusinessObject() {
        InMemoryProcessEngine engine = engineWithUserTaskEndingProcess();
        engine.start(command("request-1"));

        assertThrows(IllegalStateException.class, () -> engine.start(command("request-2")));
    }

    @Test
    void shouldPreferTenantDefinitionAndKeepInstanceDefinitionSnapshot() {
        InMemoryProcessEngine engine = new InMemoryProcessEngine();
        registerUserTaskEndingProcess(engine);
        engine.register(new ProcessDefinition(
                "tenant-a",
                "ENTRUSTMENT_REVIEW",
                2,
                List.of(
                        new ProcessNode("start", ProcessNodeType.START),
                        new ProcessNode("tenant-review", ProcessNodeType.USER_TASK),
                        new ProcessNode("end", ProcessNodeType.END)),
                List.of(
                        new ProcessTransition("start", "START", "tenant-review"),
                        new ProcessTransition("tenant-review", "APPROVE", "end"))));

        ProcessInstance tenantInstance = engine.start(command("tenant-a", "order-a", "request-a"));
        ProcessInstance sharedInstance = engine.start(command("tenant-b", "order-b", "request-b"));

        assertEquals(2, tenantInstance.definitionVersion());
        assertEquals("tenant-review", engine.taskFor(tenantInstance.id()).nodeKey());
        assertEquals(1, sharedInstance.definitionVersion());
        assertEquals("review", engine.taskFor(sharedInstance.id()).nodeKey());
    }

    private static InMemoryProcessEngine engineWithUserTaskEndingProcess() {
        InMemoryProcessEngine engine = new InMemoryProcessEngine();
        registerUserTaskEndingProcess(engine);
        return engine;
    }

    private static void registerUserTaskEndingProcess(InMemoryProcessEngine engine) {
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
    }

    private static void registerAssignedProcess(InMemoryProcessEngine engine) {
        engine.register(new ProcessDefinition(
                "ENTRUSTMENT_REVIEW",
                1,
                List.of(
                        new ProcessNode("start", ProcessNodeType.START),
                        new ProcessNode(
                                "review",
                                ProcessNodeType.USER_TASK,
                                Set.of(),
                                Set.of("reviewer"),
                                Duration.ofMinutes(5)),
                        new ProcessNode("end", ProcessNodeType.END)),
                List.of(
                        new ProcessTransition("start", "START", "review"),
                        new ProcessTransition("review", "APPROVE", "end"))));
    }

    private static ProcessInstanceCommand command(String requestId) {
        return command("ENTRUSTMENT_REVIEW", requestId);
    }

    private static com.chaos.process.ProcessTask claim(
            InMemoryProcessEngine engine, ProcessTask task, String operatorId) {
        return engine.claim(new ProcessTaskClaimCommand(
                task.tenantId(), task.id(), operatorId, Set.of(), task.version()));
    }

    private static com.chaos.process.ProcessTask complete(
            InMemoryProcessEngine engine,
            ProcessTask task,
            String event,
            String operatorId,
            String comment,
            String requestId) {
        return engine.complete(new ProcessTaskCompletionCommand(
                task.tenantId(), task.id(), event, operatorId, comment, task.version(), requestId));
    }

    private static ProcessInstanceCommand command(String processCode, String requestId) {
        return command("tenant-a", "order-1", processCode, requestId);
    }

    private static ProcessInstanceCommand command(String tenantId, String businessId, String requestId) {
        return command(tenantId, businessId, "ENTRUSTMENT_REVIEW", requestId);
    }

    private static ProcessInstanceCommand command(
            String tenantId,
            String businessId,
            String processCode,
            String requestId) {
        return new ProcessInstanceCommand(
                tenantId, processCode, "ENTRUSTMENT", businessId, "WT-1",
                Map.of(), requestId, "creator");
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
