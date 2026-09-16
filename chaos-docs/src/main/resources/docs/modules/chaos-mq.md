# chaos-mq

## 职责

MQ 能力域聚合目录。契约模块位于 `chaos-mq/chaos-mq`，对外 artifactId 为 `chaos-mq`；JDBC outbox、Kafka、RocketMQ 适配器位于同一能力域目录下。

核心契约不依赖任何 Spring、JDBC 或 MQ SDK，提供：

| 类型 | 说明 |
| --- | --- |
| `MessageEnvelope` | 标准消息信封（messageId、topic、tag、payload、headers、createdAt） |
| `MessageHeaders` | 统一消息头名：`message-id`、`message-tag` |
| `MessagePublisher` | 真实 MQ 发送端口；失败必须抛出 `MessagePublishException` |
| `OutboxPublisher` | 业务事务内写 outbox 的端口（`ReliableMessagePublisher` 实现） |
| `OutboxMessageRepository` | outbox 仓储端口：claim、`completeClaim`、`deleteSentMessagesBefore` |
| `ReliableMessageDispatcher` | 到期消息派发器（单条隔离、owner 校验、死信） |
| `OutboxDispatchScheduler` | 独立线程的定时派发与清理调度器 |
| `ReliableMessage` / `ReliableMessageStatus` | 可靠消息及状态：PENDING、SENDING、SENT、RETRYING、DEAD_LETTER、CANCELED |
| `RetryBackoffStrategy` / `DeadLetterMessageHandler` | 重试退避、死信处理扩展点；默认 `LoggingDeadLetterMessageHandler` |
| `IdempotentMessageConsumer` | 两段式（PROCESSING/DONE）幂等消费包装 |
| `ContextRestoringMessageConsumer` | 消费期间还原 trace、租户、用户上下文 |
| `MessageRetryLaterException` | 并发重复投递时要求 MQ 稍后重投 |

## 依赖方式

业务应用使用 `chaos-mq-starter`；domain/application 只需接口时依赖 `chaos-mq`。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-mq-starter</artifactId>
</dependency>
```

## 可靠消息（outbox）链路

```text
业务事务 ──► OutboxPublisher.publish()  ──► chaos_mq_outbox (PENDING)
                                               │
OutboxDispatchScheduler（每个实例一个线程）       ▼
   └► ReliableMessageDispatcher.dispatchDueMessages()
        1. claimDueMessages：条件 UPDATE 抢占为 SENDING（含 claim 超时回收，回收计入 retry_times）
        2. MessagePublisher.publish：同步等待 broker 确认，失败抛异常
        3. completeClaim：仅当 status=SENDING 且 claim_owner=本实例 时回写 SENT / RETRYING / DEAD_LETTER
   └► purgeSentMessages：按保留期分批删除 SENT
```

业务代码：

```java
@Service
public class OrderApplicationService {

    private final OutboxPublisher outboxPublisher;
    private final TransactionExecutor transactionExecutor;

    public void createOrder(CreateOrderCommand command) {
        transactionExecutor.execute(() -> {
            Order order = orderRepository.save(Order.create(...));
            // 与业务数据同一个本地事务，要么一起提交，要么一起回滚
            outboxPublisher.publish(new MessageEnvelope<>(
                    event.eventId(), "order.created", "created", payload, Map.of(), event.occurredAt()));
        });
    }
}
```

完整示例见 `chaos-examples/example-order-service` 的 `OrderApplicationService`。

## 自动装配

| 自动装配类 | 生效条件 | 注册的 Bean |
| --- | --- | --- |
| `ChaosMqAutoConfiguration` | classpath 有 Kafka / RocketMQ 且存在对应 Template Bean | `MessagePublisher`（`KafkaMessagePublisher` / `RocketMqMessagePublisher`） |
| `ChaosMqAutoConfiguration` | 存在 `JdbcOperations`，`chaos.mq.outbox.enabled=true` | `OutboxMessageRepository`（JDBC） |
| `ChaosMqOutboxAutoConfiguration` | 存在 `OutboxMessageRepository` | `OutboxPublisher`、`DeadLetterMessageHandler`（日志）、`RetryBackoffStrategy` |
| `ChaosMqOutboxAutoConfiguration` | 同时存在仓储与 `MessagePublisher`，`chaos.mq.outbox.dispatcher.enabled=true` | `ReliableMessageDispatcher`、`OutboxDispatchLifecycle`（随容器启停的调度器） |
| `ChaosMqOutboxAutoConfiguration` | 存在 `OutboxMessageRepository`，`chaos.mq.outbox.health.enabled=true` | `OutboxHealth`、`OutboxHealthIndicator`（有 Actuator 时）、待派发 gauge（有 Micrometer 时） |

拆成两个自动装配类，是为了让 outbox 的 `@ConditionalOnBean` 能稳定看到基础设施 Bean。
可选中间件相关的 Bean 放在带 `@ConditionalOnClass` 的嵌套配置里，classpath 缺少 Kafka 或 RocketMQ 时不会因方法签名反射失败。

`chaos-mq-starter` 聚合 `chaos-autoconfigure`（mq）、`chaos-mq-jdbc`、`chaos-mq-kafka` 与 `chaos-mq-rocketmq`；
Kafka / RocketMQ 的 SDK 由业务按需引入。

## 配置

```yaml
chaos:
  mq:
    publisher:
      send-timeout: 10s          # 等待 broker 确认的超时，超时视为失败
    outbox:
      enabled: true              # 是否注册 JDBC outbox 仓储与写入端
      table-name: chaos_mq_outbox
      claim-timeout: 5m          # SENDING 超过该时间允许其他实例回收，应明显大于 send-timeout
      dispatcher:
        enabled: true            # 同时存在 outbox 仓储和真实 MessagePublisher 时自动运行
        initial-delay: 10s
        interval: 1s             # 两轮派发间隔；满批时会立即继续下一轮
        batch-size: 100
        max-retry-times: 10      # 超过后进入死信
        retry-backoff: 30s
      cleanup:
        enabled: true
        retention: 7d            # SENT 消息保留时长
        interval: 1h
        batch-size: 500
```

## 消费端

```java
MessageConsumer<OrderCreated> business = message -> orderService.handle(message.payload());

MessageConsumer<OrderCreated> consumer = new ContextRestoringMessageConsumer<>(
        new IdempotentMessageConsumer<>(business, idempotentRepository,
                Duration.ofDays(1),        // DONE 标记保留时长（幂等窗口）
                Duration.ofMinutes(5),     // PROCESSING 标记 TTL，应大于单条最大处理耗时
                "order-service"),          // 消费组，多个服务共用 Redis 时必须区分
        "order-service");

// 在 Kafka/RocketMQ listener 中把原生消息转换为 MessageEnvelope 后调用
consumer.consume(envelope);
```

- `IdempotentMessageConsumer`：先写短 TTL 的 PROCESSING 标记；业务成功后才写 DONE。进程崩溃时 PROCESSING 很快过期，重投可以重新处理；并发重复投递抛 `MessageRetryLaterException`，listener 应让 MQ 稍后重投，而不是 ack。
- `ContextRestoringMessageConsumer`：从 `X-Trace-Id`、`traceparent`、`X-Tenant-Id`、`X-User-Id` 等消息头还原上下文，消费结束恢复原上下文。MyBatis 租户插件在 `missing-tenant-behavior=deny` 下依赖该上下文。

## 扩展点

- 注册 `MessagePublisher` Bean 替换默认 Kafka/RocketMQ 发布器；实现必须在发送失败时抛出异常。
- 注册 `OutboxMessageRepository` 使用其他存储；支持多实例时必须覆盖 `claimDueMessages(now, limit, claimTimeoutAt)` 和 `completeClaim`。
- 注册 `DeadLetterMessageHandler` 对接告警、死信表或人工补偿；注册 `RetryBackoffStrategy` 实现指数退避。
- 注册 `ReliableMessageDispatcher` 或关闭 `dispatcher.enabled` 后自行调度。

## 注意事项

- 引入 starter 且同时存在 JDBC 与 MQ 时派发器默认运行，需要先创建 outbox 表（见 [chaos-mq-jdbc](chaos-mq-jdbc.md)）；
  表不存在时派发线程只记录 WARN，不会退出。
- domain / application 层只依赖 `chaos-mq` 的接口，不依赖 Kafka 或 RocketMQ SDK。

- 语义是 at-least-once：发布成功但回写失败时，claim 超时后会重发，消费端必须幂等。
- `ReliableMessagePublisher` 只实现 `OutboxPublisher`，不再实现 `MessagePublisher`。旧代码把它注入为 `MessagePublisher` 的地方需要改为注入 `OutboxPublisher`。
- `ReliableMessage.lastError` 会截断到 1024 字符，与表字段一致。
- 消息头默认视为可信（来自内部服务），不要把外部系统可直接投递的 topic 接到 `ContextRestoringMessageConsumer`。
- 升级后幂等 key 格式从 `mq:{topic}:{id}` 变为 `mq:[{group}:]{topic}:{id}:processing|done`，升级窗口内已消费的消息如被重投会再处理一次。

## 积压可观测

派发器卡住（claim 持有者崩溃、下游 broker 长时间不可用、重试全部耗尽）时消息会不断堆积，
此前只在日志里留痕，`/actuator/health` 依然是 UP，滚动发布和扩缩容都会照常放行。

- `OutboxMessageRepository.countByStatus(status)` 统计各状态条数；未实现的旧仓储走默认实现返回 `-1`，
  表示"不支持统计"而不是"积压为 0"。
- `OutboxHealth` / `OutboxHealthIndicator`（`reliable.actuate` 适配器包）暴露为
  `/actuator/health` 的 `chaosMqOutbox`；仓储不支持统计时状态为 `UNKNOWN`。
- 指标 `chaos.mq.outbox.pending`（gauge）与 `chaos.mq.outbox.dispatched{outcome=sent|retry|dead}`。

```yaml
chaos:
  mq:
    outbox:
      health:
        enabled: true
        pending-threshold: 10000
        dead-letter-threshold: 100
```

详见 [治理指标与健康检查](../capabilities/metrics.md)。
