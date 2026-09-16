# 幂等（chaos-core + chaos-web + chaos-mq + chaos-redis）

> 仓库中不存在 `chaos-idempotent` 模块。幂等契约位于 `chaos-core`（包 `com.michael.chaos.core.idempotent`），各场景适配分布在对应模块。

## 组成

| 能力 | 位置 |
| --- | --- |
| `IdempotentRepository`（saveIfAbsent / remove） | chaos-core |
| `IdempotentRecordStore`、`IdempotentRecord`、`IdempotentRecordCodec`（首次响应快照） | chaos-core |
| `@Idempotent` 注解、`IdempotentKeyGenerator`（HTTP 语义：按租户/用户/方法/路径隔离） | chaos-web（`com.michael.chaos.web.idempotent`） |
| HTTP 幂等拦截器 `IdempotentInterceptor` | chaos-web |
| HTTP 响应快照采集 `IdempotentResponseReplayFilter` | chaos-web |
| MQ 幂等消费 `IdempotentMessageConsumer` | chaos-mq |
| Redis 存储 `RedissonIdempotentRepository`、`RedissonIdempotentRecordStore` | chaos-redis |
| 内存存储 `InMemoryIdempotentRepository`、`InMemoryIdempotentRecordStore`（仅开发/测试） | chaos-core `core.idempotent.support`（由 chaos-web 自动装配） |

## 依赖方式

引入 `chaos-redis-starter` 后自动使用 Redisson 存储；没有 Redis 时 Web 场景回退内存实现，生产模式下会被 ProductionSafety 拒绝。

## 示例

```java
@PostMapping
@Idempotent
public OrderSummary createOrder(@Valid @RequestBody CreateOrderRequest request) {
    return orderApplicationService.createOrder(...);
}
```

MQ 消费：

```java
new IdempotentMessageConsumer<>(consumer, idempotentRepository, Duration.ofDays(1), Duration.ofMinutes(5), "order-service");
```

## 重复请求响应回放

默认行为是重复请求返回 `409`。这对客户端并不够用：网络超时重发时它拿到 409，却无法据此判断第一次究竟成功没有，
只能再调一次查询接口对账。开启响应回放后，重复请求直接返回首次执行的状态码、响应体和 `Location` 头，
并带上 `Idempotency-Replayed: true`，客户端可以把它当作首次响应直接使用。

```yaml
chaos:
  web:
    idempotent:
      ttl: 5m
      replay:
        enabled: true          # 默认 false
        max-body-size: 64KB    # 超出则不保存快照，重复请求退回 409
        stored-headers:        # 需要一并回放的响应头
          - Location
```

三种状态的行为：

| 首次请求状态 | 重复请求结果 |
| --- | --- |
| 已完成且成功 | 回放首次响应（`Idempotency-Replayed: true`） |
| 仍在执行中 | `409`，调用方稍后重试即可拿到回放 |
| 已失败（状态码 >= 400 或抛异常） | 占位已释放，重新执行 |

按接口关闭：响应体很大、或响应中包含一次性内容（验证码图片、预签名 URL、下载流）时用 `@Idempotent(replay = false)`，
这类响应回放给调用方没有意义，还会白白占用存储。

### 代价与边界

- **内存**：开启后所有 `POST`/`PUT`/`PATCH`/`DELETE` 的响应体会经 `ContentCachingResponseWrapper` 缓存一份，
  受 `max-body-size` 约束；`GET`/`HEAD` 不受影响。
- **一次额外查询**：每个 `@Idempotent` 请求会先查一次快照再抢占位。先查快照是必要的——
  占位和快照是两次写入，占位先过期时只看占位会重新执行业务，而快照其实还在。
- **集群必须用 Redis**：内存快照只存在于处理首次请求的那个实例上，重复请求打到其他实例会退化成 409。
  生产模式下 `InMemoryIdempotentRecordStore` 会被 ProductionSafety 直接拒绝启动。
- **不校验请求体指纹**：幂等 key 已经按方法、路径、租户、用户隔离，但同一个 key 配不同请求体时框架回放旧响应而不报错。
  需要严格校验的场景请自行在 `IdempotentKeyGenerator` 中把请求体摘要并入 key。
- **异步响应不采集**：`request.isAsyncStarted()` 为真时跳过，避免存下一个空响应体。

## 注意事项

- `IdempotentRepository.remove` 的接口默认实现是空操作，自定义存储**必须覆盖**，否则失败后无法重试。
- MQ 幂等采用 PROCESSING / DONE 两段式，详见 [chaos-mq](../modules/chaos-mq.md)。
- 多个服务共用 Redis 时配置 `chaos.redis.key-prefix`，MQ 消费还应传入消费组。
- 响应快照与占位使用不同的 Redis key（快照 key 追加 `:response` 后缀）：两者生命周期不同，
  混在同一个 key 上会让失败释放把快照一起删掉。
- 快照的线格式由 `IdempotentRecordCodec` 统一定义并带版本号，解不出来时按"没有快照"重新执行，
  滚动发布期间不会因为格式变化导致接口失败。
