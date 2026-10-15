package com.chaos.process;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 无基础设施依赖的流程引擎实现，供开发环境、单元测试和能力验证使用。
 *
 * <p>该实现不提供跨进程一致性，也不替代 JDBC 持久化实现。生产环境应使用持久化实现，并在事务中完成任务和业务单据的条件更新。</p>
 */
public final class InMemoryProcessEngine implements ProcessEngine {

    private static final Logger log = LoggerFactory.getLogger(InMemoryProcessEngine.class);

    private final Clock clock;
    private final Map<String, ProcessDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, ProcessTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, String> requestInstances = new ConcurrentHashMap<>();

    /**
     * 使用系统时钟创建开发环境流程引擎。
     */
    public InMemoryProcessEngine() {
        this(Clock.systemUTC());
    }

    /**
     * 使用指定时钟创建流程引擎，测试可以传入固定时钟。
     *
     * @param clock 流程时间来源，不得为 {@code null}
     */
    public InMemoryProcessEngine(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 注册一个不可变流程定义快照。
     *
     * @param definition 已校验的流程定义
     * @throws IllegalArgumentException 相同编码和版本已注册时抛出
     */
    public void register(ProcessDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        String key = definitionKey(definition.code(), definition.version());
        if (definitions.putIfAbsent(key, definition) != null) {
            throw new IllegalArgumentException("Process definition already registered: " + key);
        }
        log.debug("Registered process definition, code={}, version={}", definition.code(), definition.version());
    }

    @Override
    public synchronized ProcessInstance start(ProcessInstanceCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId");
        requireText(command.processCode(), "processCode");
        requireText(command.businessType(), "businessType");
        requireText(command.businessId(), "businessId");
        requireText(command.requestId(), "requestId");
        String requestKey = command.tenantId() + ":" + command.requestId();
        String existingId = requestInstances.get(requestKey);
        if (existingId != null) {
            log.debug("Process start replayed, tenantId={}, processCode={}, requestId={}, instanceId={}",
                    command.tenantId(), command.processCode(), command.requestId(), existingId);
            return instances.get(existingId);
        }
        ProcessDefinition definition = definitions.values().stream()
                .filter(candidate -> candidate.code().equals(command.processCode()))
                .max(Comparator.comparingInt(ProcessDefinition::version))
                .orElseThrow(() -> new IllegalArgumentException("Process definition not found: " + command.processCode()));
        Instant now = Instant.now(clock);
        String instanceId = UUID.randomUUID().toString();
        ProcessInstance instance = new ProcessInstance(
                instanceId,
                command.tenantId(),
                definition.code(),
                definition.version(),
                command.businessType(),
                command.businessId(),
                ProcessInstanceStatus.RUNNING,
                now,
                null);
        instances.put(instanceId, instance);
        requestInstances.put(requestKey, instanceId);
        log.debug("Process instance started, tenantId={}, processCode={}, version={}, businessId={}, instanceId={}",
                command.tenantId(), definition.code(), definition.version(), command.businessId(), instanceId);
        ProcessNode start = definition.nodes().stream()
                .filter(node -> node.type() == ProcessNodeType.START)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Process has no START node"));
        ProcessTransition transition = findTransition(definition, start.key(), "START");
        ProcessNode next = findNode(definition, transition.targetNodeKey());
        if (next.type() == ProcessNodeType.USER_TASK) {
            ProcessTask task = newTask(instance, next);
            tasks.put(task.id(), task);
        } else if (next.type() == ProcessNodeType.END) {
            completeInstance(instanceId, now);
        } else {
            throw new IllegalStateException("In-memory engine only supports START -> USER_TASK or END");
        }
        return instances.get(instanceId);
    }

    @Override
    public synchronized ProcessTask claim(String taskId, String operatorId, long expectedVersion) {
        requireText(taskId, "taskId");
        requireText(operatorId, "operatorId");
        ProcessTask task = task(taskId);
        if (task.version() != expectedVersion || task.status() != ProcessTaskStatus.READY) {
            throw new IllegalStateException("Task is no longer claimable: " + taskId);
        }
        if (!task.candidateUserIds().isEmpty() && !task.candidateUserIds().contains(operatorId)) {
            throw new IllegalArgumentException("Operator is not a task candidate: " + operatorId);
        }
        ProcessTask claimed = copyTask(task, ProcessTaskStatus.CLAIMED, operatorId, task.version() + 1);
        tasks.put(taskId, claimed);
        log.debug("Process task claimed, taskId={}, operatorId={}, version={}", taskId, operatorId, claimed.version());
        return claimed;
    }

    @Override
    public synchronized ProcessTask complete(
            String taskId,
            String event,
            String operatorId,
            String comment,
            long expectedVersion,
            String requestId) {
        requireText(taskId, "taskId");
        requireText(event, "event");
        requireText(operatorId, "operatorId");
        requireText(requestId, "requestId");
        ProcessTask task = task(taskId);
        if (task.version() != expectedVersion
                || task.status() != ProcessTaskStatus.CLAIMED
                || !operatorId.equals(task.assigneeId())) {
            throw new IllegalStateException("Task is no longer completable: " + taskId);
        }
        ProcessInstance instance = instances.get(task.processInstanceId());
        ProcessDefinition definition = definitions.get(definitionKey(instance.processCode(), instance.definitionVersion()));
        ProcessTransition transition = findTransition(definition, task.nodeKey(), event);
        ProcessTask completed = copyTask(task, ProcessTaskStatus.COMPLETED, operatorId, task.version() + 1);
        tasks.put(taskId, completed);
        log.debug("Process task completed, taskId={}, event={}, operatorId={}, version={}",
                taskId, event, operatorId, completed.version());
        ProcessNode next = findNode(definition, transition.targetNodeKey());
        if (next.type() == ProcessNodeType.USER_TASK) {
            ProcessTask nextTask = newTask(instance, next);
            tasks.put(nextTask.id(), nextTask);
        } else if (next.type() == ProcessNodeType.END) {
            completeInstance(instance.id(), Instant.now(clock));
        } else {
            throw new IllegalStateException("In-memory engine only supports USER_TASK -> USER_TASK or END");
        }
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
                Set.of(),
                Set.of(),
                null,
                0);
    }

    private void completeInstance(String instanceId, Instant completedAt) {
        ProcessInstance current = instances.get(instanceId);
        instances.put(instanceId, new ProcessInstance(
                current.id(), current.tenantId(), current.processCode(), current.definitionVersion(),
                current.businessType(), current.businessId(), ProcessInstanceStatus.COMPLETED,
                current.startedAt(), completedAt));
    }

    private static ProcessTask copyTask(ProcessTask task, ProcessTaskStatus status, String assignee, long version) {
        return new ProcessTask(task.id(), task.processInstanceId(), task.nodeKey(), task.tenantId(),
                task.businessType(), task.businessId(), status, assignee, task.candidateUserIds(),
                task.candidateRoleCodes(), task.claimExpiresAt(), version);
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

    private static String definitionKey(String code, int version) {
        return code + "@" + version;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
