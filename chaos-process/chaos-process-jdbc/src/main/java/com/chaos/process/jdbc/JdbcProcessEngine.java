package com.chaos.process.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chaos.process.ProcessDefinition;
import com.chaos.process.ProcessEngine;
import com.chaos.process.ProcessInstance;
import com.chaos.process.ProcessInstanceCommand;
import com.chaos.process.ProcessInstanceStatus;
import com.chaos.process.ProcessNode;
import com.chaos.process.ProcessNodeType;
import com.chaos.process.ProcessTask;
import com.chaos.process.ProcessTaskClaimCommand;
import com.chaos.process.ProcessTaskCompletionCommand;
import com.chaos.process.ProcessTaskStatus;
import com.chaos.process.ProcessTransition;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC implementation of the process port.
 *
 * <p>The adapter deliberately keeps the definition and aggregate snapshots in JSON while
 * indexing the fields used for tenant isolation, idempotency and optimistic locking. This
 * lets applications use the same port with different JDBC dialects without leaking ORM types.
 * The schema is shipped in {@code db/chaos-process-schema-*.sql} and must be managed by the
 * application's migration tool.</p>
 */
public final class JdbcProcessEngine implements ProcessEngine {

    private final JdbcOperations jdbc;
    private final ObjectMapper mapper;
    private final String tablePrefix;
    private final TransactionTemplate transactionTemplate;

    public JdbcProcessEngine(JdbcOperations jdbc, ObjectMapper mapper) {
        this(jdbc, mapper, "chaos_process_", null);
    }

    public JdbcProcessEngine(JdbcOperations jdbc, ObjectMapper mapper, String tablePrefix,
            TransactionTemplate transactionTemplate) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.mapper.findAndRegisterModules();
        this.tablePrefix = safePrefix(tablePrefix);
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void register(ProcessDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        try {
            jdbc.update("insert into " + table("definition")
                            + " (tenant_id, process_code, definition_version, definition_json) values (?, ?, ?, ?)",
                    sqlTenant(definition.tenantId()), definition.code(), definition.version(), json(definition));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new IllegalArgumentException("Process definition already registered: "
                    + definition.code() + "@" + definition.version(), ex);
        }
    }

    @Override
    public ProcessInstance start(ProcessInstanceCommand command) {
        requireStart(command);
        return executeInTransaction(() -> startInternal(command));
    }

    private ProcessInstance startInternal(ProcessInstanceCommand command) {
        RequestRecord request = findRequest(command.tenantId(), command.requestId());
        String commandJson = json(command);
        if (request != null) {
            verifyReplay(request, "START", commandJson);
            return readInstance(request.entityId());
        }
        ProcessDefinition definition = findDefinition(command.tenantId(), command.processCode());
        ProcessNode next = startTarget(definition);
        String conflict = jdbc.query("select instance_id from " + table("instance")
                        + " where tenant_id=? and process_code=? and business_type=? and business_id=? and status=?",
                rs -> rs.next() ? rs.getString(1) : null, command.tenantId(), definition.code(),
                command.businessType(), command.businessId(), ProcessInstanceStatus.RUNNING.name());
        if (conflict != null) {
            throw new IllegalStateException("Active process instance already exists: " + conflict);
        }
        Instant now = Instant.now();
        String instanceId = UUID.randomUUID().toString();
        boolean complete = next.type() == ProcessNodeType.END;
        ProcessInstance instance = new ProcessInstance(instanceId, command.tenantId(), definition.code(),
                definition.version(), command.businessType(), command.businessId(), command.businessKey(),
                command.variables(), command.operatorId(), complete ? ProcessInstanceStatus.COMPLETED : ProcessInstanceStatus.RUNNING,
                now, complete ? now : null);
        jdbc.update("insert into " + table("instance")
                        + " (instance_id, tenant_id, process_code, definition_version, business_type, business_id, status, instance_json)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?)",
                instanceId, command.tenantId(), definition.code(), definition.version(), command.businessType(),
                command.businessId(), instance.status().name(), json(instance));
        if (!complete) {
            insertTask(newTask(instance, next));
        }
        saveRequest(command.tenantId(), command.requestId(), "START", instanceId, commandJson);
        return instance;
    }

    @Override
    public ProcessTask taskFor(String processInstanceId) {
        ProcessTask task = jdbc.query("select task_json from " + table("task")
                        + " where instance_id=? and status in (?, ?) order by created_at",
                rs -> rs.next() ? read(rs.getString(1), ProcessTask.class) : null,
                processInstanceId, ProcessTaskStatus.READY.name(), ProcessTaskStatus.CLAIMED.name());
        if (task == null) {
            throw new IllegalArgumentException("Active process task not found: " + processInstanceId);
        }
        return task;
    }

    @Override
    public ProcessTask claim(ProcessTaskClaimCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        requireText(command.taskId(), "taskId");
        requireText(command.operatorId(), "operatorId");
        ProcessTask task = readTask(command.taskId());
        if (!command.tenantId().equals(task.tenantId()) || !candidate(task, command)) {
            throw new IllegalArgumentException("Operator is not allowed to claim task: " + command.taskId());
        }
        if (task.version() != command.expectedVersion()
                || (task.status() != ProcessTaskStatus.READY && !expired(task))) {
            throw new IllegalStateException("Task is no longer claimable: " + command.taskId());
        }
        ProcessNode node = nodeFor(task);
        Instant now = Instant.now();
        ProcessTask claimed = new ProcessTask(task.id(), task.processInstanceId(), task.nodeKey(), task.tenantId(),
                task.businessType(), task.businessId(), ProcessTaskStatus.CLAIMED, command.operatorId(),
                task.candidateUserIds(), task.candidateRoleCodes(),
                node.claimLease() == null ? null : now.plus(node.claimLease()), null, null, null, task.version() + 1);
        int updated = jdbc.update("update " + table("task") + " set status=?, assignee_id=?, claim_expires_at=?, version=?, task_json=?"
                        + " where task_id=? and tenant_id=? and version=? and (status=? or (status=? and claim_expires_at <= ?))",
                claimed.status().name(), claimed.assigneeId(), timestamp(claimed.claimExpiresAt()), claimed.version(), json(claimed),
                task.id(), command.tenantId(), task.version(), ProcessTaskStatus.READY.name(), ProcessTaskStatus.CLAIMED.name(), timestamp(now));
        if (updated != 1) {
            throw new IllegalStateException("Task is no longer claimable: " + command.taskId());
        }
        return claimed;
    }

    @Override
    public ProcessTask complete(ProcessTaskCompletionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        requireText(command.taskId(), "taskId");
        requireText(command.event(), "event");
        requireText(command.operatorId(), "operatorId");
        requireText(command.requestId(), "requestId");
        return executeInTransaction(() -> completeInternal(command));
    }

    private ProcessTask completeInternal(ProcessTaskCompletionCommand command) {
        RequestRecord request = findRequest(command.tenantId(), command.requestId());
        String commandJson = json(command);
        if (request != null) {
            verifyReplay(request, "COMPLETE", commandJson);
            return readTask(request.entityId());
        }
        ProcessTask task = readTask(command.taskId());
        if (!command.tenantId().equals(task.tenantId()) || task.version() != command.expectedVersion()
                || task.status() != ProcessTaskStatus.CLAIMED || !command.operatorId().equals(task.assigneeId())
                || expired(task)) {
            throw new IllegalStateException("Task is no longer completable: " + command.taskId());
        }
        ProcessInstance instance = readInstance(task.processInstanceId());
        ProcessDefinition definition = findDefinition(instance.tenantId(), instance.processCode(), instance.definitionVersion());
        ProcessTransition transition = definition.transitions().stream()
                .filter(t -> t.sourceNodeKey().equals(task.nodeKey()) && t.event().equals(command.event()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Process transition not found"));
        ProcessNode next = definition.nodes().stream().filter(n -> n.key().equals(transition.targetNodeKey())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Process node not found"));
        if (next.type() != ProcessNodeType.USER_TASK && next.type() != ProcessNodeType.END) {
            throw new IllegalStateException("JDBC process engine only supports USER_TASK and END nodes");
        }
        Instant now = Instant.now();
        ProcessTask completed = new ProcessTask(task.id(), task.processInstanceId(), task.nodeKey(), task.tenantId(),
                task.businessType(), task.businessId(), ProcessTaskStatus.COMPLETED, task.assigneeId(), task.candidateUserIds(),
                task.candidateRoleCodes(), null, now, command.event(), command.comment(), task.version() + 1);
        int updated = jdbc.update("update " + table("task") + " set status=?, completed_at=?, completion_event=?, comment=?, version=?, task_json=?"
                        + " where task_id=? and tenant_id=? and version=? and status=?",
                completed.status().name(), timestamp(now), completed.completionEvent(), completed.comment(), completed.version(), json(completed),
                task.id(), command.tenantId(), task.version(), ProcessTaskStatus.CLAIMED.name());
        if (updated != 1) throw new IllegalStateException("Task is no longer completable: " + command.taskId());
        if (next.type() == ProcessNodeType.END) {
            ProcessInstance done = new ProcessInstance(instance.id(), instance.tenantId(), instance.processCode(), instance.definitionVersion(),
                    instance.businessType(), instance.businessId(), instance.businessKey(), instance.variables(), instance.startedBy(),
                    ProcessInstanceStatus.COMPLETED, instance.startedAt(), now);
            jdbc.update("update " + table("instance") + " set status=?, instance_json=? where instance_id=? and status=?",
                    done.status().name(), json(done), instance.id(), ProcessInstanceStatus.RUNNING.name());
        } else {
            insertTask(newTask(instance, next));
        }
        saveRequest(command.tenantId(), command.requestId(), "COMPLETE", task.id(), commandJson);
        return completed;
    }

    private ProcessDefinition findDefinition(String tenantId, String code) {
        List<ProcessDefinition> definitions = jdbc.query("select definition_json from " + table("definition")
                        + " where process_code=? and (tenant_id=? or tenant_id='')",
                (rs, row) -> read(rs.getString(1), ProcessDefinition.class), code, sqlTenant(tenantId));
        return definitions.stream()
                .max(Comparator.<ProcessDefinition>comparingInt(d -> Objects.equals(sqlTenant(d.tenantId()), sqlTenant(tenantId)) ? 1 : 0)
                        .thenComparingInt(ProcessDefinition::version))
                .orElseThrow(() -> new IllegalArgumentException("Process definition not found: " + code));
    }

    private ProcessDefinition findDefinition(String tenantId, String code, int version) {
        List<ProcessDefinition> definitions = jdbc.query("select definition_json from " + table("definition")
                        + " where process_code=? and definition_version=? and (tenant_id=? or tenant_id='')",
                (rs, row) -> read(rs.getString(1), ProcessDefinition.class), code, version, sqlTenant(tenantId));
        return definitions.stream()
                .max(Comparator.comparingInt(d -> Objects.equals(sqlTenant(d.tenantId()), sqlTenant(tenantId)) ? 1 : 0))
                .orElseThrow(() -> new IllegalArgumentException("Process definition not found: " + code + "@" + version));
    }

    private ProcessNode startTarget(ProcessDefinition definition) {
        ProcessNode start = definition.nodes().stream().filter(n -> n.type() == ProcessNodeType.START).findFirst().orElseThrow();
        ProcessTransition transition = definition.transitions().stream().filter(t -> t.sourceNodeKey().equals(start.key()) && t.event().equals("START"))
                .findFirst().orElseThrow(() -> new IllegalStateException("START transition not found"));
        ProcessNode next = definition.nodes().stream().filter(n -> n.key().equals(transition.targetNodeKey())).findFirst().orElseThrow();
        if (next.type() != ProcessNodeType.USER_TASK && next.type() != ProcessNodeType.END) {
            throw new IllegalStateException("JDBC process engine only supports USER_TASK and END nodes");
        }
        return next;
    }

    private ProcessTask newTask(ProcessInstance instance, ProcessNode node) {
        return new ProcessTask(UUID.randomUUID().toString(), instance.id(), node.key(), instance.tenantId(), instance.businessType(),
                instance.businessId(), ProcessTaskStatus.READY, null, node.candidateUserIds(), node.candidateRoleCodes(), null,
                null, null, null, 0);
    }

    private void insertTask(ProcessTask task) {
        jdbc.update("insert into " + table("task")
                        + " (task_id, instance_id, tenant_id, node_key, status, assignee_id, claim_expires_at, completed_at, completion_event, comment, version, created_at, task_json)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", task.id(), task.processInstanceId(), task.tenantId(), task.nodeKey(),
                task.status().name(), task.assigneeId(), timestamp(task.claimExpiresAt()), timestamp(task.completedAt()), task.completionEvent(),
                task.comment(), task.version(), timestamp(Instant.now()), json(task));
    }

    private ProcessTask readTask(String id) {
        try {
            return jdbc.queryForObject("select task_json from " + table("task") + " where task_id=?",
                    (rs, row) -> read(rs.getString(1), ProcessTask.class), id);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Process task not found: " + id, ex);
        }
    }

    private ProcessInstance readInstance(String id) {
        return jdbc.queryForObject("select instance_json from " + table("instance") + " where instance_id=?",
                (rs, row) -> read(rs.getString(1), ProcessInstance.class), id);
    }

    private ProcessNode nodeFor(ProcessTask task) {
        ProcessInstance instance = readInstance(task.processInstanceId());
        return findDefinition(task.tenantId(), instance.processCode(), instance.definitionVersion()).nodes().stream()
                .filter(n -> n.key().equals(task.nodeKey())).findFirst().orElseThrow();
    }

    private RequestRecord findRequest(String tenant, String requestId) {
        return jdbc.query("select request_type, entity_id, command_json from " + table("request")
                        + " where tenant_id=? and request_id=?",
                rs -> rs.next() ? new RequestRecord(rs.getString(1), rs.getString(2), rs.getString(3)) : null,
                tenant, requestId);
    }

    private void saveRequest(String tenant, String requestId, String type, String entityId, String commandJson) {
        jdbc.update("insert into " + table("request") + " (tenant_id, request_id, request_type, entity_id, command_json) values (?, ?, ?, ?, ?)",
                tenant, requestId, type, entityId, commandJson);
    }

    private static void verifyReplay(RequestRecord request, String expectedType, String commandJson) {
        if (!expectedType.equals(request.type()) || !commandJson.equals(request.commandJson())) {
            throw new IllegalArgumentException("Request ID already used with different command");
        }
    }

    private <T> T executeInTransaction(java.util.function.Supplier<T> supplier) {
        return transactionTemplate == null ? supplier.get() : transactionTemplate.execute(status -> supplier.get());
    }

    private String table(String suffix) { return tablePrefix + suffix; }
    private static String safePrefix(String value) {
        String prefix = value == null || value.isBlank() ? "chaos_process_" : value;
        if (!prefix.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid process table prefix");
        return prefix;
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (JsonProcessingException ex) { throw new IllegalStateException("Process JSON serialization failed", ex); } }
    private <T> T read(String value, Class<T> type) { try { return mapper.readValue(value, type); } catch (JsonProcessingException ex) { throw new IllegalStateException("Process JSON deserialization failed", ex); } }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static boolean expired(ProcessTask task) { return task.claimExpiresAt() != null && !task.claimExpiresAt().isAfter(Instant.now()); }
    private static boolean candidate(ProcessTask task, ProcessTaskClaimCommand command) {
        return (task.candidateUserIds().isEmpty() && task.candidateRoleCodes().isEmpty())
                || task.candidateUserIds().contains(command.operatorId())
                || command.operatorRoleCodes().stream().anyMatch(task.candidateRoleCodes()::contains);
    }
    private static void requireStart(ProcessInstanceCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId"); requireText(command.processCode(), "processCode");
        requireText(command.businessType(), "businessType"); requireText(command.businessId(), "businessId");
        requireText(command.requestId(), "requestId"); requireText(command.operatorId(), "operatorId");
    }
    private static void requireText(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); }
    private static String sqlTenant(String tenantId) { return tenantId == null ? "" : tenantId; }

    private record RequestRecord(String type, String entityId, String commandJson) {
    }
}
