# Chaos 状态机与流程引擎能力

## 目标

Chaos 将状态机和流程编排拆成两个层次：

- 状态机负责校验业务状态是否允许执行某个事件，并执行转换后的领域动作。
- 流程引擎负责流程定义版本、流程实例、用户待办、候选人、领取和完成。

流程引擎不能绕过业务状态机直接修改业务单据状态。业务模块仍然负责金额、数据完整性、租户权限和状态转换前置条件。

当前第一阶段的无基础设施契约和状态机内核位于独立的 `chaos-process` 模块（`com.chaos.process`），只依赖 `chaos-core` 的异常契约，不依赖 Spring、数据库或消息组件。后续 JDBC、Redis、消息和 Starter 实现只能依赖这些端口。

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
  └─ chaos-process：状态机、流程契约和 InMemoryProcessEngine，生产持久化实现按同一端口扩展
     JDBC/Redis/MQ 适配器（后续模块）
```

`StateMachineEngine` 是无状态对象，不保存当前状态，也不使用内存锁替代数据库并发控制。应用服务应在同一事务内执行：加载单据、调用状态机 Guard、按期望状态或版本更新单据、写状态历史。

## 状态机 API

使用 `StateMachineDefinition` 声明转换：

```java
StateMachineDefinition<OrderStatus, OrderEvent, SubmitContext> definition =
    StateMachineDefinition.<OrderStatus, OrderEvent, SubmitContext>builder()
        .transition(OrderStatus.DRAFT, OrderEvent.SUBMIT, OrderStatus.PENDING_REVIEW,
            List.of(context -> context.hasTasks()),
            (source, event, target, context) -> context.recordSubmission())
        .transition(OrderStatus.PENDING_REVIEW, OrderEvent.REJECT,
            OrderStatus.REJECTED)
        .build();

StateMachineResult<OrderStatus, OrderEvent> result =
    new StateMachineEngine().fire(definition, current, event, context);
```

同一 `(source, event)` 只能声明一条转换；重复定义在启动期失败。Guard 失败返回 `GUARD_REJECTED`，未声明转换返回 `INVALID_TRANSITION`。

状态机只执行内存中的转换和动作。动作需要写数据库或发布事件时，必须由业务应用服务在外层事务中提供，不能让通用核心直接依赖 Repository。

## 流程定义与实例

`ProcessDefinition` 是已发布版本的不可变快照，包括：

- `ProcessNode`：`START`、`USER_TASK`、`SERVICE_TASK`、`EXCLUSIVE_GATEWAY`、`END`。
- `ProcessTransition`：来源节点、事件和目标节点。
- `code + version`：流程的稳定标识和版本。

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

`requestId` 是启动和完成操作的幂等键。后续持久化实现应对 `(tenant_id, request_id)` 建唯一约束。Redis 锁只能作为削峰措施，不能替代数据库条件更新。

领取任务使用租约截止时间；租约过期后允许重新领取或转派，不使用永久锁定。

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

当前版本已提供独立 `chaos-process` 模块中的状态机定义、Guard、Action、执行引擎、流程定义节点/连线、实例和待办契约，以及仅用于开发和测试的 `InMemoryProcessEngine`。尚未实现 JDBC Repository、自动配置、管理端 API、会签、定时器、消息重试和流程设计器；这些属于后续基础设施层，不应提前塞进无依赖的核心模块。
