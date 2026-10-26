package com.chaos.process.redis;

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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.Comparator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis process engine backed by JSON snapshots and Lua compare-and-set updates.
 *
 * <p>Definitions, instances, tasks and idempotency records are indexed in Redis sets. Task
 * claim and completion use a Lua CAS script so two application instances cannot overwrite the
 * same task version. Redis persistence/replication must be configured by the application when
 * process durability is required.</p>
 */
public final class RedisProcessEngine implements ProcessEngine {

    private static final DefaultRedisScript<Long> CAS_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('get', KEYS[1]) "
                    + "if not current then return 0 end "
                    + "if not string.find(current, ARGV[1], 1, true) then return 0 end "
                    + "redis.call('set', KEYS[1], ARGV[2]) return 1", Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String prefix;

    public RedisProcessEngine(StringRedisTemplate redis, ObjectMapper mapper) {
        this(redis, mapper, "chaos:process");
    }

    public RedisProcessEngine(StringRedisTemplate redis, ObjectMapper mapper, String prefix) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.mapper.findAndRegisterModules();
        if (prefix == null || prefix.isBlank() || !prefix.matches("[A-Za-z0-9:_-]+")) {
            throw new IllegalArgumentException("Invalid process Redis key prefix");
        }
        this.prefix = prefix;
    }

    @Override
    public void register(ProcessDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        String key = definitionKey(definition.tenantId(), definition.code(), definition.version());
        Boolean created = redis.opsForValue().setIfAbsent(key, json(definition));
        if (!Boolean.TRUE.equals(created)) throw new IllegalArgumentException("Process definition already registered: " + key);
        redis.opsForSet().add(index("definitions"), key);
    }

    @Override
    public synchronized ProcessInstance start(ProcessInstanceCommand command) {
        requireStart(command);
        String request = requestKey(command.tenantId(), command.requestId());
        String commandJson = json(command);
        String requestJson = redis.opsForValue().get(request);
        if (requestJson != null) {
            RequestResult result = read(requestJson, RequestResult.class);
            if (!result.commandJson().equals(commandJson)) throw new IllegalArgumentException("Request ID already used with different command");
            return read(redis.opsForValue().get(result.entityKey()), ProcessInstance.class);
        }
        ProcessDefinition definition = findDefinition(command.tenantId(), command.processCode());
        ProcessNode next = startTarget(definition);
        String active = activeKey(command.tenantId(), definition.code(), command.businessType(), command.businessId());
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(active, "pending"))) {
            throw new IllegalStateException("Active process instance already exists");
        }
        Instant now = Instant.now();
        String instanceKey = instanceKey(UUID.randomUUID().toString());
        boolean complete = next.type() == ProcessNodeType.END;
        String id = instanceKey.substring(instanceKey.lastIndexOf(':') + 1);
        ProcessInstance instance = new ProcessInstance(id, command.tenantId(), definition.code(), definition.version(), command.businessType(),
                command.businessId(), command.businessKey(), command.variables(), command.operatorId(),
                complete ? ProcessInstanceStatus.COMPLETED : ProcessInstanceStatus.RUNNING, now, complete ? now : null);
        redis.opsForValue().set(instanceKey, json(instance));
        redis.opsForSet().add(index("instances"), instanceKey);
        if (!complete) {
            ProcessTask task = newTask(instance, next);
            redis.opsForValue().set(taskKey(task.id()), json(task));
            redis.opsForSet().add(instanceTasksKey(instance.id()), taskKey(task.id()));
            redis.opsForSet().add(index("tasks"), taskKey(task.id()));
        }
        redis.opsForValue().set(active, instanceKey);
        redis.opsForValue().set(request, json(new RequestResult(instanceKey, commandJson)));
        return instance;
    }

    @Override
    public ProcessTask taskFor(String processInstanceId) {
        var keys = redis.opsForSet().members(instanceTasksKey(processInstanceId));
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("Active process task not found: " + processInstanceId);
        }
        for (String key : keys) {
            ProcessTask task = read(redis.opsForValue().get(key), ProcessTask.class);
            if (task.status() == ProcessTaskStatus.READY || task.status() == ProcessTaskStatus.CLAIMED) return task;
        }
        throw new IllegalArgumentException("Active process task not found: " + processInstanceId);
    }

    @Override
    public ProcessTask claim(ProcessTaskClaimCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId"); requireText(command.taskId(), "taskId"); requireText(command.operatorId(), "operatorId");
        String key = taskKey(command.taskId());
        ProcessTask task = read(redis.opsForValue().get(key), ProcessTask.class);
        if (!command.tenantId().equals(task.tenantId()) || !candidate(task, command)) throw new IllegalArgumentException("Operator is not a task candidate");
        if (task.version() != command.expectedVersion() || (task.status() != ProcessTaskStatus.READY && !expired(task))) {
            throw new IllegalStateException("Task is no longer claimable: " + command.taskId());
        }
        ProcessNode node = nodeFor(task);
        Instant now = Instant.now();
        ProcessTask claimed = new ProcessTask(task.id(), task.processInstanceId(), task.nodeKey(), task.tenantId(), task.businessType(), task.businessId(),
                ProcessTaskStatus.CLAIMED, command.operatorId(), task.candidateUserIds(), task.candidateRoleCodes(),
                node.claimLease() == null ? null : now.plus(node.claimLease()), null, null, null, task.version() + 1);
        Long result = redis.execute(CAS_SCRIPT, java.util.List.of(key), "\"version\":" + task.version(), json(claimed));
        if (!Long.valueOf(1L).equals(result)) throw new IllegalStateException("Task was changed by another worker");
        return claimed;
    }

    @Override
    public synchronized ProcessTask complete(ProcessTaskCompletionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.tenantId(), "tenantId"); requireText(command.taskId(), "taskId"); requireText(command.event(), "event");
        requireText(command.operatorId(), "operatorId"); requireText(command.requestId(), "requestId");
        String request = requestKey(command.tenantId(), command.requestId());
        String requestJson = redis.opsForValue().get(request);
        String commandJson = json(command);
        if (requestJson != null) {
            RequestResult replay = read(requestJson, RequestResult.class);
            if (!replay.commandJson().equals(commandJson)) {
                throw new IllegalArgumentException("Request ID already used with different command");
            }
            return read(redis.opsForValue().get(replay.entityKey()), ProcessTask.class);
        }
        ProcessTask task = read(redis.opsForValue().get(taskKey(command.taskId())), ProcessTask.class);
        if (!command.tenantId().equals(task.tenantId()) || task.version() != command.expectedVersion() || task.status() != ProcessTaskStatus.CLAIMED
                || !command.operatorId().equals(task.assigneeId()) || expired(task)) throw new IllegalStateException("Task is no longer completable");
        ProcessInstance instance = read(redis.opsForValue().get(instanceKey(task.processInstanceId())), ProcessInstance.class);
        ProcessDefinition definition = findDefinition(instance.tenantId(), instance.processCode(), instance.definitionVersion());
        ProcessTransition transition = definition.transitions().stream().filter(t -> t.sourceNodeKey().equals(task.nodeKey()) && t.event().equals(command.event())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Process transition not found"));
        ProcessNode next = definition.nodes().stream().filter(n -> n.key().equals(transition.targetNodeKey())).findFirst().orElseThrow();
        if (next.type() != ProcessNodeType.USER_TASK && next.type() != ProcessNodeType.END) {
            throw new IllegalStateException("Redis process engine only supports USER_TASK and END nodes");
        }
        Instant now = Instant.now();
        ProcessTask completed = new ProcessTask(task.id(), task.processInstanceId(), task.nodeKey(), task.tenantId(), task.businessType(), task.businessId(),
                ProcessTaskStatus.COMPLETED, task.assigneeId(), task.candidateUserIds(), task.candidateRoleCodes(), null, now, command.event(), command.comment(), task.version() + 1);
        Long result = redis.execute(CAS_SCRIPT, java.util.List.of(taskKey(task.id())), "\"version\":" + task.version(), json(completed));
        if (!Long.valueOf(1L).equals(result)) throw new IllegalStateException("Task was changed by another worker");
        if (next.type() == ProcessNodeType.END) {
            ProcessInstance done = new ProcessInstance(instance.id(), instance.tenantId(), instance.processCode(), instance.definitionVersion(), instance.businessType(), instance.businessId(),
                    instance.businessKey(), instance.variables(), instance.startedBy(), ProcessInstanceStatus.COMPLETED, instance.startedAt(), now);
            redis.opsForValue().set(instanceKey(instance.id()), json(done));
            redis.delete(activeKey(instance.tenantId(), instance.processCode(), instance.businessType(), instance.businessId()));
        } else {
            ProcessTask nextTask = newTask(instance, next);
            redis.opsForValue().set(taskKey(nextTask.id()), json(nextTask));
            redis.opsForSet().add(instanceTasksKey(instance.id()), taskKey(nextTask.id()));
            redis.opsForSet().add(index("tasks"), taskKey(nextTask.id()));
        }
        redis.opsForValue().set(request, json(new RequestResult(taskKey(task.id()), commandJson)));
        return completed;
    }

    private ProcessDefinition findDefinition(String tenant, String code) {
        return redis.opsForSet().members(index("definitions")).stream().map(k -> read(redis.opsForValue().get(k), ProcessDefinition.class))
                .filter(d -> d.code().equals(code) && (Objects.equals(d.tenantId(), tenant) || d.tenantId() == null))
                .max(Comparator.<ProcessDefinition>comparingInt(d -> Objects.equals(d.tenantId(), tenant) ? 1 : 0)
                        .thenComparingInt(ProcessDefinition::version))
                .orElseThrow(() -> new IllegalArgumentException("Process definition not found: " + code));
    }
    private ProcessDefinition findDefinition(String tenant, String code, int version) {
        return redis.opsForSet().members(index("definitions")).stream().map(k -> read(redis.opsForValue().get(k), ProcessDefinition.class))
                .filter(d -> d.code().equals(code) && d.version() == version && (Objects.equals(d.tenantId(), tenant) || d.tenantId() == null))
                .max(Comparator.<ProcessDefinition>comparingInt(d -> Objects.equals(d.tenantId(), tenant) ? 1 : 0)).orElseThrow();
    }
    private ProcessNode startTarget(ProcessDefinition d) { ProcessNode s = d.nodes().stream().filter(n -> n.type() == ProcessNodeType.START).findFirst().orElseThrow();
        ProcessTransition t = d.transitions().stream().filter(x -> x.sourceNodeKey().equals(s.key()) && x.event().equals("START")).findFirst().orElseThrow();
        ProcessNode n = d.nodes().stream().filter(x -> x.key().equals(t.targetNodeKey())).findFirst().orElseThrow();
        if (n.type() != ProcessNodeType.USER_TASK && n.type() != ProcessNodeType.END) throw new IllegalStateException("Redis process engine only supports USER_TASK and END nodes"); return n; }
    private ProcessTask newTask(ProcessInstance i, ProcessNode n) { return new ProcessTask(UUID.randomUUID().toString(), i.id(), n.key(), i.tenantId(), i.businessType(), i.businessId(), ProcessTaskStatus.READY, null, n.candidateUserIds(), n.candidateRoleCodes(), null, null, null, null, 0); }
    private ProcessNode nodeFor(ProcessTask t) { ProcessInstance i = read(redis.opsForValue().get(instanceKey(t.processInstanceId())), ProcessInstance.class); return findDefinition(i.tenantId(), i.processCode(), i.definitionVersion()).nodes().stream().filter(n -> n.key().equals(t.nodeKey())).findFirst().orElseThrow(); }
    private String definitionKey(String tenant, String code, int version) { return prefix + ":definition:" + (tenant == null ? "_" : tenant) + ":" + code + ":" + version; }
    private String instanceKey(String id) { return prefix + ":instance:" + id; }
    private String taskKey(String id) { return prefix + ":task:" + id; }
    private String activeKey(String t, String c, String bt, String bi) { return prefix + ":active:" + t + ":" + c + ":" + bt + ":" + bi; }
    private String requestKey(String t, String r) { return prefix + ":request:" + t + ":" + r; }
    private String instanceTasksKey(String id) { return prefix + ":instance-tasks:" + id; }
    private String index(String name) { return prefix + ":index:" + name; }
    private String json(Object o) { try { return mapper.writeValueAsString(o); } catch (JsonProcessingException e) { throw new IllegalStateException("Process JSON serialization failed", e); } }
    private <T> T read(String s, Class<T> t) { if (s == null) throw new IllegalArgumentException("Process record not found"); try { return mapper.readValue(s, t); } catch (JsonProcessingException e) { throw new IllegalStateException("Process JSON deserialization failed", e); } }
    private static boolean expired(ProcessTask t) { return t.claimExpiresAt() != null && !t.claimExpiresAt().isAfter(Instant.now()); }
    private static boolean candidate(ProcessTask t, ProcessTaskClaimCommand c) { return (t.candidateUserIds().isEmpty() && t.candidateRoleCodes().isEmpty()) || t.candidateUserIds().contains(c.operatorId()) || c.operatorRoleCodes().stream().anyMatch(t.candidateRoleCodes()::contains); }
    private static void requireStart(ProcessInstanceCommand c) { Objects.requireNonNull(c, "command must not be null"); requireText(c.tenantId(), "tenantId"); requireText(c.processCode(), "processCode"); requireText(c.businessType(), "businessType"); requireText(c.businessId(), "businessId"); requireText(c.requestId(), "requestId"); requireText(c.operatorId(), "operatorId"); }
    private static void requireText(String v, String n) { if (v == null || v.isBlank()) throw new IllegalArgumentException(n + " must not be blank"); }
    private record RequestResult(String entityKey, String commandJson) { }
}
