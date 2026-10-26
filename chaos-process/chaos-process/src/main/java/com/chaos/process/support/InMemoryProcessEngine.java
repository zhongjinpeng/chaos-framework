package com.chaos.process.support;

import com.chaos.core.metrics.ChaosMeterNames;
import com.chaos.core.metrics.ChaosMetrics;
import com.chaos.core.metrics.NoopChaosMetrics;
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
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 无基础设施依赖的流程引擎实现，供开发环境、单元测试和能力验证使用。
 *
 * <p>该实现不提供跨进程一致性，也不替代 JDBC 持久化实现。生产环境应使用持久化实现，并在事务中完成任务和业务单据的条件更新。</p>
 */
@SuppressWarnings("removal")
public final class InMemoryProcessEngine implements ProcessEngine {

    private static final Logger log = LoggerFactory.getLogger(InMemoryProcessEngine.class);

    private final Clock clock;
    private final ChaosMetrics metrics;
    private final Map<DefinitionKey, ProcessDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, ProcessDefinition> instanceDefinitions = new ConcurrentHashMap<>();
    private final Map<String, ProcessTask> tasks = new ConcurrentHashMap<>();
    private final Map<RequestKey, RequestResult> requests = new ConcurrentHashMap<>();
    private final Map<ActiveBusinessKey, String> activeInstances = new ConcurrentHashMap<>();

    /**
     * 使用系统时钟创建开发环境流程引擎。
     */
    public InMemoryProcessEngine() {
        this(Clock.systemUTC(), NoopChaosMetrics.instance());
    }

    /**
     * 使用指定时钟创建流程引擎，测试可以传入固定时钟。
     *
     * @param clock 流程时间来源，不得为 {@code null}
     */
    public InMemoryProcessEngine(Clock clock) {
        this(clock, NoopChaosMetrics.instance());
    }

    /**
     * 使用指定时钟和治理指标端口创建流程引擎。
     *
     * @param clock 流程时间来源，不得为 {@code null}
     * @param metrics 指标上报端口；为空时使用空实现
     */
    public InMemoryProcessEngine(Clock clock, ChaosMetrics metrics) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
    }

    /**
     * 注册一个不可变流程定义快照。
     *
     * @param definition 已校验的流程定义
     * @throws IllegalArgumentException 相同编码和版本已注册时抛出
     */
    public void register(ProcessDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        DefinitionKey key = new DefinitionKey(definition.tenantId(), definition.code(), definition.version());
        if (definitions.putIfAbsent(key, definition) != null) {
            throw new IllegalArgumentException("Process definition already registered: " + key);
        }
        log.debug("Registered process definition, tenantId={}, code={}, version={}",
                definition.tenantId(), definition.code(), definition.version());
    }

    @Override
    public synchronized ProcessInstance start(ProcessInstanceCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        requireText(command.processCode(), "processCode");
        requireText(command.businessType(), "businessType");
        requireText(command.businessId(), "businessId");
        requireText(command.requestId(), "requestId");
        requireText(command.operatorId(), "operatorId");
        RequestKey requestKey = new RequestKey(command.tenantId(), command.requestId());
        RequestResult existing = requests.get(requestKey);
        if (existing != null) {
            requireMatchingRequest(existing, RequestType.START, command, command.requestId());
            log.debug("Process start replayed, tenantId={}, processCode={}, requestId={}, instanceId={}",
                    command.tenantId(), command.processCode(), command.requestId(), existing.entityId());
            record(ChaosMeterNames.PROCESS_INSTANCES_STARTED, "replayed");
            return instances.get(existing.entityId());
        }
        ProcessDefinition definition = findDefinition(command.tenantId(), command.processCode());
        ProcessNode start = definition.nodes().stream()
                .filter(node -> node.type() == ProcessNodeType.START)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Process has no START node"));
        ProcessTransition transition = findTransition(definition, start.key(), "START");
        ProcessNode next = findNode(definition, transition.targetNodeKey());
        requireSupportedNextNode(next, "START");

        ActiveBusinessKey activeBusinessKey = new ActiveBusinessKey(
                command.tenantId(), definition.code(), command.businessType(), command.businessId());
        String activeInstanceId = activeInstances.get(activeBusinessKey);
        if (activeInstanceId != null) {
            record(ChaosMeterNames.PROCESS_INSTANCES_STARTED, "conflict");
            throw new IllegalStateException("Active process instance already exists: " + activeInstanceId);
        }

        Instant now = Instant.now(clock);
        String instanceId = UUID.randomUUID().toString();
        boolean completesImmediately = next.type() == ProcessNodeType.END;
        ProcessInstance instance = new ProcessInstance(
                instanceId,
                command.tenantId(),
                definition.code(),
                definition.version(),
                command.businessType(),
                command.businessId(),
                command.businessKey(),
                command.variables(),
                command.operatorId(),
                completesImmediately ? ProcessInstanceStatus.COMPLETED : ProcessInstanceStatus.RUNNING,
                now,
                completesImmediately ? now : null);
        ProcessTask initialTask = completesImmediately ? null : newTask(instance, next);

        instances.put(instanceId, instance);
        instanceDefinitions.put(instanceId, definition);
        if (initialTask != null) {
            tasks.put(initialTask.id(), initialTask);
            activeInstances.put(activeBusinessKey, instanceId);
        }
        requests.put(requestKey, new RequestResult(RequestType.START, instanceId, command));
        log.debug("Process instance started, tenantId={}, processCode={}, version={}, businessId={}, instanceId={}",
                command.tenantId(), definition.code(), definition.version(), command.businessId(), instanceId);
        record(ChaosMeterNames.PROCESS_INSTANCES_STARTED, "started");
        return instance;
    }

    @Override
    public synchronized ProcessTask claim(ProcessTaskClaimCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        String taskId = command.taskId();
        String operatorId = command.operatorId();
        requireText(taskId, "taskId");
        requireText(operatorId, "operatorId");
        ProcessTask task = task(taskId);
        if (!task.tenantId().equals(command.tenantId())) {
            record(ChaosMeterNames.PROCESS_TASKS_CLAIMED, "forbidden");
            throw new IllegalArgumentException("Task does not belong to tenant: " + command.tenantId());
        }
        Instant now = Instant.now(clock);
        boolean expiredClaim = task.status() == ProcessTaskStatus.CLAIMED
                && task.claimExpiresAt() != null
                && !task.claimExpiresAt().isAfter(now);
        if (task.version() != command.expectedVersion()
                || (task.status() != ProcessTaskStatus.READY && !expiredClaim)) {
            record(ChaosMeterNames.PROCESS_TASKS_CLAIMED, "conflict");
            throw new IllegalStateException("Task is no longer claimable: " + taskId);
        }
        if (!isCandidate(task, operatorId, command.operatorRoleCodes())) {
            record(ChaosMeterNames.PROCESS_TASKS_CLAIMED, "forbidden");
            throw new IllegalArgumentException("Operator is not a task candidate: " + operatorId);
        }
        ProcessNode node = nodeFor(task);
        Instant claimExpiresAt = node.claimLease() == null ? null : now.plus(node.claimLease());
        ProcessTask claimed = claimedTask(task, operatorId, claimExpiresAt);
        tasks.put(taskId, claimed);
        log.debug("Process task claimed, taskId={}, operatorId={}, version={}", taskId, operatorId, claimed.version());
        record(ChaosMeterNames.PROCESS_TASKS_CLAIMED, "claimed");
        return claimed;
    }

    @Override
    public synchronized ProcessTask complete(ProcessTaskCompletionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        String taskId = command.taskId();
        String event = command.event();
        String operatorId = command.operatorId();
        String requestId = command.requestId();
        requireText(taskId, "taskId");
        requireText(event, "event");
        requireText(operatorId, "operatorId");
        requireText(requestId, "requestId");
        ProcessTask task = task(taskId);
        if (!task.tenantId().equals(command.tenantId())) {
            record(ChaosMeterNames.PROCESS_TASKS_COMPLETED, "conflict");
            throw new IllegalArgumentException("Task does not belong to tenant: " + command.tenantId());
        }
        RequestKey requestKey = new RequestKey(command.tenantId(), requestId);
        RequestResult existing = requests.get(requestKey);
        if (existing != null) {
            requireMatchingRequest(existing, RequestType.COMPLETE, command, requestId);
            log.debug("Process task completion replayed, taskId={}, requestId={}", taskId, requestId);
            record(ChaosMeterNames.PROCESS_TASKS_COMPLETED, "replayed");
            return task(existing.entityId());
        }
        Instant now = Instant.now(clock);
        if (task.version() != command.expectedVersion()
                || task.status() != ProcessTaskStatus.CLAIMED
                || !operatorId.equals(task.assigneeId())
                || isExpired(task, now)) {
            record(ChaosMeterNames.PROCESS_TASKS_COMPLETED, "conflict");
            throw new IllegalStateException("Task is no longer completable: " + taskId);
        }
        ProcessInstance instance = instances.get(task.processInstanceId());
        ProcessDefinition definition = instanceDefinitions.get(instance.id());
        ProcessTransition transition = findTransition(definition, task.nodeKey(), event);
        ProcessNode next = findNode(definition, transition.targetNodeKey());
        requireSupportedNextNode(next, "USER_TASK");

        ProcessTask completed = completedTask(task, event, command.comment(), now);
        ProcessTask nextTask = next.type() == ProcessNodeType.USER_TASK ? newTask(instance, next) : null;
        ProcessInstance completedInstance = next.type() == ProcessNodeType.END
                ? completedInstance(instance, now)
                : null;

        tasks.put(taskId, completed);
        if (nextTask != null) {
            tasks.put(nextTask.id(), nextTask);
        }
        if (completedInstance != null) {
            instances.put(instance.id(), completedInstance);
            activeInstances.remove(activeBusinessKey(instance), instance.id());
        }
        requests.put(requestKey, new RequestResult(RequestType.COMPLETE, taskId, command));
        log.debug("Process task completed, taskId={}, event={}, operatorId={}, version={}",
                taskId, event, operatorId, completed.version());
        record(ChaosMeterNames.PROCESS_TASKS_COMPLETED, "completed");
        return completed;
    }

    private ProcessTask task(String taskId) {
        ProcessTask task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Process task not found: " + taskId);
        }
        return task;
    }

    /**
     * 返回指定流程实例当前的一条用户任务，供开发环境查询和测试使用。
     *
     * @param processInstanceId 流程实例 ID
     * @return 当前任务；不存在时抛出异常
     */
    public ProcessTask taskFor(String processInstanceId) {
        return tasks.values().stream()
                .filter(task -> task.processInstanceId().equals(processInstanceId))
                .filter(task -> task.status() == ProcessTaskStatus.READY || task.status() == ProcessTaskStatus.CLAIMED)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Active process task not found: " + processInstanceId));
    }

    private ProcessTask newTask(ProcessInstance instance, ProcessNode node) {
        return new ProcessTask(
                UUID.randomUUID().toString(),
                instance.id(),
                node.key(),
                instance.tenantId(),
                instance.businessType(),
                instance.businessId(),
                ProcessTaskStatus.READY,
                null,
                node.candidateUserIds(),
                node.candidateRoleCodes(),
                null,
                null,
                null,
                null,
                0);
    }

    private static ProcessInstance completedInstance(ProcessInstance current, Instant completedAt) {
        return new ProcessInstance(
                current.id(), current.tenantId(), current.processCode(), current.definitionVersion(),
                current.businessType(), current.businessId(), current.businessKey(), current.variables(),
                current.startedBy(), ProcessInstanceStatus.COMPLETED,
                current.startedAt(), completedAt);
    }

    private static ProcessTask claimedTask(ProcessTask task, String assignee, Instant claimExpiresAt) {
        return new ProcessTask(
                task.id(), task.processInstanceId(), task.nodeKey(), task.tenantId(), task.businessType(),
                task.businessId(), ProcessTaskStatus.CLAIMED, assignee, task.candidateUserIds(),
                task.candidateRoleCodes(), claimExpiresAt, null, null, null, task.version() + 1);
    }

    private static ProcessTask completedTask(ProcessTask task, String event, String comment, Instant completedAt) {
        return new ProcessTask(
                task.id(), task.processInstanceId(), task.nodeKey(), task.tenantId(), task.businessType(),
                task.businessId(), ProcessTaskStatus.COMPLETED, task.assigneeId(), task.candidateUserIds(),
                task.candidateRoleCodes(), null, completedAt, event, comment, task.version() + 1);
    }

    private static ProcessNode findNode(ProcessDefinition definition, String key) {
        return definition.nodes().stream().filter(node -> node.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Process node not found: " + key));
    }

    private static ProcessTransition findTransition(ProcessDefinition definition, String source, String event) {
        return definition.transitions().stream()
                .filter(transition -> transition.sourceNodeKey().equals(source) && transition.event().equals(event))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Process transition not found: " + source + " + " + event));
    }

    private static void requireSupportedNextNode(ProcessNode node, String sourceType) {
        if (node.type() != ProcessNodeType.USER_TASK && node.type() != ProcessNodeType.END) {
            throw new IllegalStateException(
                    "In-memory engine only supports " + sourceType + " -> USER_TASK or END");
        }
    }

    private ProcessNode nodeFor(ProcessTask task) {
        ProcessDefinition definition = instanceDefinitions.get(task.processInstanceId());
        return findNode(definition, task.nodeKey());
    }

    private ProcessDefinition findDefinition(String tenantId, String processCode) {
        Comparator<ProcessDefinition> byVersion = Comparator.comparingInt(ProcessDefinition::version);
        return definitions.values().stream()
                .filter(candidate -> tenantId.equals(candidate.tenantId()))
                .filter(candidate -> candidate.code().equals(processCode))
                .max(byVersion)
                .or(() -> definitions.values().stream()
                        .filter(candidate -> candidate.tenantId() == null)
                        .filter(candidate -> candidate.code().equals(processCode))
                        .max(byVersion))
                .orElseThrow(() -> new IllegalArgumentException("Process definition not found: " + processCode));
    }

    private static boolean isCandidate(ProcessTask task, String operatorId, Set<String> operatorRoleCodes) {
        if (task.candidateUserIds().isEmpty() && task.candidateRoleCodes().isEmpty()) {
            return true;
        }
        if (task.candidateUserIds().contains(operatorId)) {
            return true;
        }
        return operatorRoleCodes.stream().anyMatch(task.candidateRoleCodes()::contains);
    }

    private static boolean isExpired(ProcessTask task, Instant now) {
        return task.claimExpiresAt() != null && !task.claimExpiresAt().isAfter(now);
    }

    private void requireMatchingRequest(
            RequestResult result,
            RequestType expectedType,
            Object expectedRequest,
            String requestId) {
        if (result.type() != expectedType || !result.request().equals(expectedRequest)) {
            String metricName = expectedType == RequestType.START
                    ? ChaosMeterNames.PROCESS_INSTANCES_STARTED
                    : ChaosMeterNames.PROCESS_TASKS_COMPLETED;
            record(metricName, "conflict");
            throw new IllegalArgumentException("Request ID already used with different command: " + requestId);
        }
    }

    private static ActiveBusinessKey activeBusinessKey(ProcessInstance instance) {
        return new ActiveBusinessKey(
                instance.tenantId(), instance.processCode(), instance.businessType(), instance.businessId());
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private void record(String name, String outcome) {
        metrics.increment(name, ChaosMeterNames.TAG_OUTCOME, outcome);
    }

    private enum RequestType {
        START,
        COMPLETE
    }

    private record RequestKey(String tenantId, String requestId) {
    }

    private record RequestResult(RequestType type, String entityId, Object request) {
    }

    private record ActiveBusinessKey(
            String tenantId,
            String processCode,
            String businessType,
            String businessId) {
    }

    private record DefinitionKey(String tenantId, String processCode, int version) {
    }
}
