# chaos-mq-jdbc

## 职责

JDBC outbox 可靠消息持久化适配器。源码目录为 `chaos-mq/chaos-mq-jdbc`，对外 artifactId 为 `chaos-mq-jdbc`。

该模块实现 `OutboxMessageRepository`，只负责 outbox 表读写，不直接依赖 Kafka、RocketMQ 或其他 broker SDK。

## 依赖方式

业务服务通常通过 `chaos-mq-starter` 间接引入。

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-mq-starter</artifactId>
</dependency>
```

## 配置

存在 `JdbcOperations` Bean 时，`chaos-autoconfigure`（mq） 自动注册 `JdbcOutboxMessageRepository` 和 `OutboxPublisher`；再存在真实 `MessagePublisher` 时自动启动派发器。完整配置见 [chaos-mq](chaos-mq.md)。

```yaml
chaos:
  mq:
    outbox:
      enabled: true
      table-name: chaos_mq_outbox   # 只允许普通标识符，可带 schema 前缀
      claim-timeout: 5m
```

## 表结构

模块内置三份方言脚本（classpath 路径）：

| 脚本 | 适用数据库 |
| --- | --- |
| `db/chaos-mq-outbox-schema-mysql.sql` | MySQL 8.0+ / MariaDB 10.5+（`longtext`、`datetime(3)`、索引写在建表语句中） |
| `db/chaos-mq-outbox-schema-postgresql.sql` | PostgreSQL 12+（`text`、`timestamptz`） |
| `db/chaos-mq-outbox-schema-h2.sql` | H2（本地/测试） |
| `db/chaos-mq-outbox-schema.sql` | 与 H2 版相同，保留给旧引用 |

索引：

| 索引 | 服务的查询 |
| --- | --- |
| `(status, next_retry_at, created_at)` | 到期抢占 |
| `(status, claimed_at)` | 发送超时回收 |
| `(status, updated_at)` | 已发送消息清理 |

从旧版本升级时，为已有表补充后两个索引：

```sql
-- MySQL
alter table chaos_mq_outbox add index idx_chaos_mq_outbox_claimed (status, claimed_at),
                            add index idx_chaos_mq_outbox_updated (status, updated_at);
-- PostgreSQL
create index if not exists idx_chaos_mq_outbox_claimed on chaos_mq_outbox (status, claimed_at);
create index if not exists idx_chaos_mq_outbox_updated on chaos_mq_outbox (status, updated_at);
```

生产环境请通过 Flyway、Liquibase 或企业 DDL 平台执行。

## 多实例语义

1. **抢占**：先查候选，再执行 `update ... where message_id=? and status=? and retry_times=? and (到期条件 or 超时条件)`，只有一个实例能更新成功，并写入 `claimed_at`、`claim_owner`。
2. **超时回收**：SENDING 超过 `claim-timeout` 的消息允许其他实例重新抢占，同时 `retry_times + 1`；超过 `max-retry-times` 后直接进入死信，不会被无限回收。
3. **结果回写**：`completeClaim` 追加 `status='SENDING' and claim_owner=本实例`。实例 A 卡住、实例 B 回收并发送成功后，A 迟到的失败结果不会把 SENT 覆盖成 RETRYING。
4. **清理**：`deleteSentMessagesBefore` 先按索引查出一批 message_id 再逐条删除，避免 `DELETE ... LIMIT` 的方言差异和长时间锁表。

## 注意事项

- outbox 表必须和业务写库处于同一个本地事务，才能保证业务数据与消息记录一致。
- 条件 claim 不等价于 broker exactly-once，消费端仍必须幂等。
- `claim-timeout` 需要明显大于 `chaos.mq.publisher.send-timeout`，避免慢发送被其他实例过早回收。
- `findByMessageId` 只在记录不存在时返回空，数据库连接异常会直接抛出。
- payload 使用 Jackson JSON 存储，跨服务消费建议使用稳定的事件 DTO，不要直接序列化持久化实体。

## 边界

- `chaos-mq` 本身保持无 JDBC、无 Kafka、无 RocketMQ SDK 依赖，落库实现只在本模块。
- JDBC outbox 只负责持久化，不负责具体 broker 的发送。
- 条件 claim（按 `message_id` + 状态 + 到期时间抢占更新）能收窄多实例重复派发的窗口，
  但**不能替代消费端幂等**——消费侧仍需 `IdempotentMessageConsumer`。
- 支持 `SKIP LOCKED` 的数据库可以按方言提供更强的抢占实现，当前实现取的是跨数据库可移植的最大公约数。
