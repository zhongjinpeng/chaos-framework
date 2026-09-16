# chaos-redis

## 职责

基于 Redisson 的基础设施实现：分布式锁、幂等存储、集群限流、布隆过滤器、延迟队列、Lua 执行、带前缀的缓存 key 策略。锁、幂等、限流、缓存 key 的**接口**位于 `chaos-core` / `chaos-domain`（不存在独立的 chaos-lock、chaos-idempotent、chaos-cache 模块）。

| 接口（模块） | Redis 实现 |
| --- | --- |
| `com.michael.chaos.core.lock.DistributedLock`（chaos-core） | `RedissonDistributedLock` |
| `com.michael.chaos.core.idempotent.IdempotentRepository`（chaos-core） | `RedissonIdempotentRepository` |
| `com.michael.chaos.core.idempotent.IdempotentRecordStore`（chaos-core） | `RedissonIdempotentRecordStore` |
| `com.michael.chaos.core.ratelimit.RateLimiter`（chaos-core） | `RedissonRateLimiter` |
| `com.michael.chaos.domain.cache.CacheKeyStrategy`（chaos-domain） | `PrefixedCacheKeyStrategy` |
| `JwtRevocationService`（chaos-security-api） | 不在本模块：统一实现 `RedisJwtRevocationService` 位于 chaos-security-redis，由 `ChaosSecurityRedisAutoConfiguration` 装配 |

## 依赖方式

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-redis-starter</artifactId>
</dependency>
```

## 配置

连接使用 Redisson Spring Boot Starter 标准配置（`spring.data.redis.*` 或 `spring.redis.redisson.*`）。

```yaml
chaos:
  redis:
    # 幂等、锁、限流、缓存 key、布隆过滤器、延迟队列的统一前缀，默认空（兼容旧 key）
    key-prefix: ${spring.application.name}
```

- 多个服务共用同一个 Redis 时**强烈建议**配置前缀，否则同名 key 会互相冲突（例如 A 服务消费过的消息会让 B 服务跳过）。
- 修改前缀相当于切换到新 key：滚动发布期间新旧实例的锁不互斥，延迟队列和布隆过滤器的存量数据不会迁移，请在发布窗口内评估。
- JWT 黑名单需要在授权服务和资源服务之间共享，**不受前缀影响**，固定为 `chaos:security:jwt:blacklist:`。

## 组件行为

### RedissonDistributedLock

- `leaseTime` 为 `null`/0/负数时启用 Redisson watchdog 自动续期；显式租约时 watchdog 关闭。
- 释放时如果锁已因租约到期被释放，输出 WARN 而不是静默返回；`execute` 在业务结束时发现锁已过期会输出 ERROR，提醒临界区可能失去互斥。
- `waitTime` 为 `null` 表示不等待；重复 `unlock` 安全。

```java
// 执行时间不可预估：不传租约，启用 watchdog
distributedLock.execute("order:submit:" + orderId, Duration.ofSeconds(1), null, () -> submit(orderId));
```

### RedissonIdempotentRepository

- `saveIfAbsent` 使用 `SET NX PX` 原子写入；TTL 必须为正。
- `remove` 真正删除 key。旧版本没有实现 `remove`（接口默认空操作），导致 MQ 消费失败后重投被丢弃、HTTP 失败后重试被拒。

### RedissonRateLimiter

一个 Lua 脚本内完成滑动窗口计数：

1. 用 Redis 服务端 `TIME` 计算当前窗口，避免各实例时钟偏差；
2. 按上一窗口剩余比例加权估算：`previous * (1 - elapsed) + current`，消除固定窗口边界 2 倍流量；
3. 未超限才 `INCR` 并设置过期时间，判断与自增原子执行。

key 形如 `{prefix}chaos:rate-limit:{业务key}:{窗口序号}`，业务 key 包在 hash tag 中，Redis Cluster 下窗口 key 位于同一 slot。

### BloomFilterTemplate

必须先 `create(name, expectedInsertions, falseProbability)`。未初始化时 `add` 抛出明确的 `IllegalStateException`，`mightContain` 返回 `false`。

### DelayQueueTemplate

基于 `RDelayedQueue`，Redisson 3.4x 起已标记 deprecated（官方替代 `RReliableQueue` 需要 Redisson PRO）。只适合可靠性要求不高的场景；需要可靠投递请使用 MQ 延迟消息或 outbox + 定时任务。

## 自动装配顺序

`ChaosRedisAutoConfiguration` 在 `RedissonAutoConfigurationV2` 之后、`ChaosSecurityAutoConfiguration`、`ChaosAuthorizationAutoConfiguration`、`ChaosWebAutoConfiguration` 之前执行，保证 Redis 实现先于 Noop/InMemory 兜底实现注册。JWT 黑名单 Bean 放在 `@ConditionalOnClass` 嵌套配置中，未引入 chaos-security 的应用不会因缺类启动失败。

## 测试

```bash
# 单元测试（Mockito）
./mvnw -pl chaos-data/chaos-redis -am test
# 真实 Redis 集成测试（Testcontainers，需要 Docker）
./mvnw -pl chaos-data/chaos-redis,chaos-security/chaos-security-redis -am -Pchaos-integration-test verify
```

JWT 黑名单集成测试位于 `chaos-security/chaos-security-redis/src/integration-test`（实现类所在模块）。

## 注意事项

- 禁止新增 `RedisUtil` 大杂烩；按锁、布隆过滤器、延迟队列、Lua、幂等分别建模。
- `RedissonIdempotentRecordStore` 的快照 key 在幂等 key 后追加 `:response`，与占位 key 分离；
  固定使用 `StringCodec`，业务替换 Redisson 全局编解码器不会导致滚动发布期间读写格式不一致。
- `LuaScriptExecutor` 使用 Redisson 默认 codec 编码参数；传字符串参数时建议自行使用 `StringCodec` 的 `RScript`。
