# Chaos 状态机与流程引擎能力

## 目标

Chaos 将状态机和流程编排拆成两个层次：

- 状态机负责校验业务状态是否允许执行某个事件，并执行转换后的领域动作。
- 流程引擎负责流程定义版本、流程实例、用户待办、候选人、领取和完成。

流程引擎不能绕过业务状态机直接修改业务单据状态。业务模块仍然负责金额、数据完整性、租户权限和状态转换前置条件。

无基础设施契约和状态机内核位于独立的 `chaos-process` 模块。流程契约位于 `com.chaos.process`，状态机内核位于 `com.chaos.process.statemachine`，开发和测试适配器位于 `com.chaos.process.support`。该模块只依赖 `chaos-core`，不依赖 Spring、数据库或消息组件。Spring Boot 接入由 `chaos-process-starter` 和 `chaos-autoconfigure` 提供；JDBC、Redis 适配器通过独立模块实现这些端口。

## 分层

```text
业务模块
  ├─ 领域状态、事件和 Guard
  ├─ ProcessAdapter：提交校验、审核人解析、完成回调
  └─ 业务单据仓储

Chaos 流程能力
  ├─ StateMachineEngine：同步执行状态转换
  ├─ ProcessDefinition：不可变、可版本化的流程快照
  ├─ ProcessEngine：启动、领取、完成任务端口
  ├─ chaos-process：状态机、流程契约和 support.InMemoryProcessEngine
  ├─ chaos-process-starter：Spring Boot 依赖入口和内存实现条件装配
  ├─ chaos-process-jdbc / chaos-process-jdbc-starter：JDBC 持久化实现
  └─ chaos-process-redis / chaos-process-redis-starter：Redis 持久化实现
```

`StateMachineEngine` 是无状态对象，不保存当前状态，也不使用内存锁替代数据库并发控制。应用服务应在同一事务内执行：加载单据、调用状态机 Guard、按期望状态或版本更新单据、写状态历史。

## Spring Boot 接入

应用按部署模式引入能力 Starter：

```xml
<dependency>
    <groupId>com.chaos</groupId>
    <artifactId>chaos-process-starter</artifactId>
</dependency>
```

生产环境可选择 JDBC 或 Redis：

```yaml
chaos:
  process:
    engine: jdbc # memory、jdbc、redis、none
```

对应坐标为 `com.chaos:chaos-process-jdbc-starter` 和 `com.chaos:chaos-process-redis-starter`。JDBC 适配器不会自动建表，请使用应用的 Flyway/Liquibase 或迁移工具执行 `db/chaos-process-schema-h2.sql`（并按目标数据库方言调整）；Redis 适配器要求配置持久化、复制和 Lua 脚本支持。`engine=none` 表示由应用自行提供 `ProcessEngine` Bean。

开发和测试环境默认创建 `InMemoryProcessEngine`：

```yaml
chaos:
  process:
    enabled: true
    engine: memory
```

应用自行声明 `ProcessEngine` Bean 时自动装配会回退。生产实现应同时设置 `chaos.process.engine=none`，避免创建内存实现；若生产模式仍存在 `InMemoryProcessEngine`，统一生产安全检查默认阻断启动。自动装配还会提供接入统一 `ChaosMetrics` 的 `StateMachineEngine` Bean；需要状态机指标时应注入该 Bean，不要自行 `new StateMachineEngine()`。`chaos.process.enabled=false` 会完全关闭流程自动装配。

## 状态机 API

使用 `StateMachineDefinition` 声明转换：

```java
import com.chaos.process.statemachine.StateMachineDefinition;
import com.chaos.process.statemachine.StateMachineEngine;
import com.chaos.process.statemachine.StateMachineResult;

StateMachineDefinition<OrderStatus, OrderEvent, SubmitContext> definition =
    StateMachineDefinition.<OrderStatus, OrderEvent, SubmitContext>builder()
        .transition(OrderStatus.DRAFT, OrderEvent.SUBMIT, OrderStatus.PENDING_REVIEW,
            List.of(context -> context.hasTasks()),
            (source, event, target, context) -> context.recordSubmission())
        .transition(OrderStatus.PENDING_REVIEW, OrderEvent.REJECT,
            OrderStatus.REJECTED)
        .build();

StateMachineResult<OrderStatus, OrderEvent> result =
stateMachineEngine.fire(definition, current, event, context);
```

同一 `(source, event)` 只能声明一条转换；重复定义在启动期失败。Guard 失败返回 `GUARD_REJECTED`，未声明转换返回 `INVALID_TRANSITION`。

状态机只执行内存中的转换和动作。动作需要写数据库或发布事件时，必须由业务应用服务在外层事务中提供，不能让通用核心直接依赖 Repository。

## 指标与运行诊断

引入 Micrometer 指标实现后，状态机和内存流程引擎发布以下低基数计数器，标签只使用固定枚举值 `outcome`，不会写入租户、流程编码、任务 ID 或业务 ID：

| 指标 | outcome |
| --- | --- |
| `chaos.process.state.transitions` | `completed`、`invalid`、`guard-rejected`、`action-error` |
| `chaos.process.instances.started` | `started`、`replayed`、`conflict` |
| `chaos.process.tasks.claimed` | `claimed`、`conflict`、`forbidden` |
| `chaos.process.tasks.completed` | `completed`、`replayed`、`conflict` |

启动报告和 `/actuator/health` 的 `chaosRuntime` 组件会展示流程能力是否启用、所选 `engine` 以及实际 `ProcessEngine` 实现，便于确认生产环境是否误用了内存实现。

## 流程定义与实例

`ProcessDefinition` 是已发布版本的不可变快照，包括：

- `ProcessNode`：`START`、`USER_TASK`、`SERVICE_TASK`、`EXCLUSIVE_GATEWAY`、`END`。
- `ProcessTransition`：来源节点、事件和目标节点。
- `tenantId + code + version`：租户级流程的稳定标识和版本；`tenantId` 为空时定义可由所有租户共享，存在租户专属定义时优先使用专属定义。

定义构造时会拒绝重复节点、悬空连线、相同来源节点和事件的重复连线、不可达节点以及无法到达结束节点的分支。当前内存运行时只执行 `START`、`USER_TASK` 和 `END`；`SERVICE_TASK`、`EXCLUSIVE_GATEWAY` 是持久化适配器的扩展契约，不能在内存运行时中执行。

已运行实例必须固定绑定定义版本。已发布版本不能原地修改，需要新建版本并重新发布：

```text
DRAFT -> PUBLISHED -> RETIRED
```

实例的生命周期为 `RUNNING`、`COMPLETED`、`CANCELLED`、`SUSPENDED`；用户任务生命周期为 `READY`、`CLAIMED`、`COMPLETED`、`CANCELLED`、`EXPIRED`。

“审核人打开了页面”不应改变业务单据状态。以委托登记管理为例，单据可以保持 `PENDING_REVIEW`，而流程任务从 `READY` 变为 `CLAIMED`。

## 事务与并发规则

完成任务必须在一个事务内完成：

1. 以租户、任务 ID、期望版本锁定待办。
2. 校验任务为 `READY` 或当前用户已领取的 `CLAIMED`。
3. 校验候选用户/角色和业务权限。
4. 调用业务状态机和业务 Guard。
5. 条件更新任务并递增版本。
6. 更新业务单据状态，写状态历史和流程事件。
7. 创建后续节点任务。

条件更新影响行数为 0 时，统一返回并发冲突，不允许覆盖其他处理人的结果：

```sql
UPDATE chaos_process_work_item
SET status = 'COMPLETED', version = version + 1
WHERE tenant_id = ?
  AND id = ?
  AND status IN ('READY', 'CLAIMED')
  AND version = ?;
```

`requestId` 是启动和完成操作的幂等键。相同租户和幂等键只允许重放内容完全相同的命令；载荷发生变化时必须返回冲突，不能静默返回第一次调用结果。JDBC 通过 `(tenant_id, request_id)` 主键保存命令快照，Redis 通过租户和请求键保存命令快照。Redis 锁只能作为削峰措施，不能替代任务版本的 Lua 条件更新。

`ProcessNode` 可为用户任务声明候选用户、候选角色和领取租约。使用 `ProcessTaskClaimCommand` 领取时必须显式传入租户和操作人角色；租约过期后允许其他候选人按最新版本重新领取。`ProcessTaskCompletionCommand` 同样显式携带租户，并保存完成事件、完成时间和处理意见。旧的长参数领取/完成方法保留一版兼容期，已经标记为 deprecated。

## 推荐持久化表

JDBC 实现建议使用以下表，字段可按数据库方言落地：

| 表 | 作用 |
| --- | --- |
| `chaos_process_definition` | 流程编码、名称和租户 |
| `chaos_process_definition_version` | 发布版本和定义 JSON 快照 |
| `chaos_process_instance` | 业务单据与流程实例绑定 |
| `chaos_process_node_instance` | 实例运行到的节点 |
| `chaos_process_work_item` | 用户待办、候选人、领取租约和处理结果 |
| `chaos_process_event` | 状态变化、任务完成和幂等事件 |

所有表必须包含 `tenant_id`。实例唯一性建议为 `(tenant_id, process_code, business_type, business_id, active)`；定义版本唯一性为 `(tenant_id, process_code, version)`；事件幂等性为 `(tenant_id, request_id)`。

## 委托登记管理接入示例

委托登记管理定义流程编码 `ENTRUSTMENT_REVIEW`：

```text
START -> REVIEW(USER_TASK)
  REVIEW -- APPROVE --> APPROVED(SERVICE_TASK) -> END
  REVIEW -- REJECT  --> REJECTED(SERVICE_TASK) -> END
```

业务单据状态和流程任务分离：

```text
委托单：DRAFT -> PENDING_REVIEW -> APPROVED / REJECTED
待办：  READY -> CLAIMED -> COMPLETED
```

委托模块适配器负责：

- 提交前校验至少一个任务、每个任务至少一个样品、每个样品至少一个检测参数。
- 根据项目、组织和角色解析审核候选人。
- 审核驳回时强制要求意见。
- 审核完成后调用委托单状态机，更新委托单并生成审计记录。
- 重新提交时保留历史审核记录，不复用旧任务实例。

## 当前实现范围

当前版本已提供独立 `chaos-process` 模块中的状态机定义、Guard、Action、执行引擎、流程定义节点/连线、实例和待办契约，以及 `com.chaos.process.support.InMemoryProcessEngine` 开发测试适配器；同时提供 Starter、Spring Boot 自动装配、生产安全检查、低基数指标和运行诊断。内存、JDBC、Redis 实现均支持 `START -> USER_TASK/END`、连续用户任务、候选用户/角色、领取租约、乐观版本、启动和完成幂等，以及同一业务对象只存在一个活动实例。JDBC 通过事务模板和数据库唯一约束保证持久化一致性；Redis 使用 Lua CAS 保护任务版本，实例和索引的一致性依赖 Redis 的持久化部署策略。

尚未实现流程定义发布管理、管理端 API、会签、定时器、消息重试和流程设计器。这些能力依赖持久化、查询与权限边界，应在相应基础设施模块中实现，不能塞进无依赖核心模块。
