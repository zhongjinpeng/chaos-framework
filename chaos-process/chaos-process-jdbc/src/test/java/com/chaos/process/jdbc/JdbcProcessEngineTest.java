package com.chaos.process.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chaos.process.ProcessDefinition;
import com.chaos.process.ProcessInstance;
import com.chaos.process.ProcessInstanceCommand;
import com.chaos.process.ProcessNode;
import com.chaos.process.ProcessNodeType;
import com.chaos.process.ProcessTask;
import com.chaos.process.ProcessTaskClaimCommand;
import com.chaos.process.ProcessTaskCompletionCommand;
import com.chaos.process.ProcessTransition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class JdbcProcessEngineTest {

    private JdbcProcessEngine engine;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:process_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/chaos-process-schema-h2.sql"))
                .execute(dataSource);
        engine = new JdbcProcessEngine(new JdbcTemplate(dataSource), new ObjectMapper());
        engine.register(definition());
    }

    @Test
    void shouldStartClaimCompleteAndReplay() {
        ProcessInstanceCommand start = new ProcessInstanceCommand(
                "tenant-a", "review", "order", "order-1", "ORD-1", Map.of(), "start-1", "maker");
        ProcessInstance instance = engine.start(start);
        ProcessTask ready = engine.taskFor(instance.id());
        ProcessTask claimed = engine.claim(new ProcessTaskClaimCommand(
                "tenant-a", ready.id(), "reviewer", Set.of(), ready.version()));
        ProcessTask completed = engine.complete(new ProcessTaskCompletionCommand(
                "tenant-a", claimed.id(), "APPROVE", "reviewer", "ok", claimed.version(), "complete-1"));

        assertThat(completed.completionEvent()).isEqualTo("APPROVE");
        assertThat(engine.start(start).id()).isEqualTo(instance.id());
        assertThat(engine.complete(new ProcessTaskCompletionCommand(
                "tenant-a", claimed.id(), "APPROVE", "reviewer", "ok", claimed.version(), "complete-1")))
                .isEqualTo(completed);
    }

    @Test
    void shouldAllowStartingAgainAfterCompletion() {
        ProcessInstanceCommand start = new ProcessInstanceCommand(
                "tenant-a", "review", "order", "order-2", null, Map.of(), "start-2", "maker");
        ProcessInstance first = engine.start(start);
        ProcessTask ready = engine.taskFor(first.id());
        ProcessTask claimed = engine.claim(new ProcessTaskClaimCommand(
                "tenant-a", ready.id(), "reviewer", Set.of(), ready.version()));
        engine.complete(new ProcessTaskCompletionCommand(
                "tenant-a", claimed.id(), "APPROVE", "reviewer", null, claimed.version(), "complete-2"));

        ProcessInstance second = engine.start(new ProcessInstanceCommand(
                "tenant-a", "review", "order", "order-2", null, Map.of(), "start-3", "maker"));
        assertThat(second.id()).isNotEqualTo(first.id());
    }

    @Test
    void shouldRejectCompletionReplayWithDifferentPayload() {
        ProcessInstance instance = engine.start(new ProcessInstanceCommand(
                "tenant-a", "review", "order", "order-3", null, Map.of(), "start-3", "maker"));
        ProcessTask ready = engine.taskFor(instance.id());
        ProcessTask claimed = engine.claim(new ProcessTaskClaimCommand(
                "tenant-a", ready.id(), "reviewer", Set.of(), ready.version()));
        engine.complete(new ProcessTaskCompletionCommand(
                "tenant-a", claimed.id(), "APPROVE", "reviewer", "ok", claimed.version(), "complete-3"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.complete(new ProcessTaskCompletionCommand(
                "tenant-a", claimed.id(), "APPROVE", "reviewer", "changed", claimed.version(), "complete-3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ProcessDefinition definition() {
        return new ProcessDefinition("tenant-a", "review", 1,
                List.of(new ProcessNode("start", ProcessNodeType.START),
                        new ProcessNode("review", ProcessNodeType.USER_TASK),
                        new ProcessNode("end", ProcessNodeType.END)),
                List.of(new ProcessTransition("start", "START", "review"),
                        new ProcessTransition("review", "APPROVE", "end")));
    }
}
