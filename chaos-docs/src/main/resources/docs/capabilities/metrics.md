# 治理指标与健康检查（chaos-core + chaos-trace + 各能力模块）

> 仓库中不存在 `chaos-metrics` 模块。上报端口位于 `chaos-core`（包 `com.michael.chaos.core.metrics`），
> Micrometer 实现位于 `chaos-trace`，埋点散落在各能力模块，装配在 `chaos-autoconfigure`。

## 解决的问题

框架替业务做了大量治理决策——限流、幂等、租户准入、权限校验、token 撤销、消息投递——但这些决策此前只写日志。
线上出现"下单成功率下跌"时，无法回答究竟是被限流挡了、被幂等挡了，还是租户被停用，只能翻日志。

另外 `MeterNames` 里声明过 `chaos.mq.publish.total`、`chaos.lock.acquire.total` 两个指标名，
但全仓没有任何代码引用它们——声明了指标名却从不发射，比没有指标更有误导性。该类已删除。

## 组成

| 能力 | 位置 |
| --- | --- |
| `ChaosMetrics`（上报端口）、`ChaosMeterNames`、`NoopChaosMetrics` | chaos-core `core.metrics` |
| `MicrometerChaosMetrics`（Micrometer 实现） | chaos-trace `trace.monitor` |
| `ChaosMetricsAutoConfiguration`（装配，先于各能力自动装配） | chaos-autoconfigure `autoconfigure.metrics` |
| `OutboxHealth` / `OutboxHealthIndicator` | chaos-mq `mq.reliable` |

没有 Micrometer 或没有 `MeterRegistry` 时自动注册 `NoopChaosMetrics`，各组件无条件注入，
调用点不必写判空分支——散落的判空既是噪音，也容易漏掉某个分支导致埋点缺失。

## 指标清单

全部指标带 `framework=chaos` 公共标签（由 `chaos-application-starter` 的 `MeterRegistryCustomizer` 添加）。

| 指标 | 类型 | 标签 | 含义 |
| --- | --- | --- | --- |
| `chaos.ratelimit.rejected` | counter | `source`（web/gateway）、`dimension` | 被限流拒绝的请求数 |
| `chaos.ratelimit.errors` | counter | `source`、`outcome`（fail-open/fail-closed） | 限流器调用失败数（Redis 不可用等） |
| `chaos.idempotent.rejected` | counter | — | 被幂等拒绝的重复请求（首次仍在执行中） |
| `chaos.idempotent.replayed` | counter | — | 命中响应回放的重复请求 |
| `chaos.idempotent.record.skipped` | counter | `reason`（too-large/async/error） | 响应快照未保存的次数 |
| `chaos.tenant.denied` | counter | `source`、`reason`（租户状态） | 租户准入被拒次数 |
| `chaos.security.access.denied` | counter | `source`（permission/data-scope） | 权限校验被拒次数 |
| `chaos.security.auth.failed` | counter | `source`、`reason` | 认证失败次数 |
| `chaos.security.token.revoked` | counter | `source` | token 撤销检查命中次数 |
| `chaos.mq.outbox.dispatched` | counter | `outcome`（sent/retry/dead） | outbox 派发结果 |
| `chaos.mq.outbox.pending` | gauge | — | outbox 待派发条数 |
| `chaos.audit.events` | counter | `outcome`（queued/published/failed/dropped） | 审计事件投递情况 |

### 标签基数

`increment` 的标签只允许低基数取值（原因、结果、维度名）。**不得传入用户 ID、租户 ID、幂等 key、
异常消息等高基数值**，否则会把时序库打爆。网关认证失败的 `reason` 因此使用调用点给出的固定枚举
（`missing-token` / `no-decoder` / `invalid-token`），异常原文只进审计事件。

`chaos.mq.outbox.pending` 在仓储不支持统计时返回 `-1`，在 Prometheus 上表现为负值，
一眼能看出"没接上"而不是"积压为 0"。

## outbox 健康检查

派发器卡住（claim 持有者崩溃、下游 broker 长时间不可用、重试全部耗尽）此前只在日志里留痕：
消息不断堆积，但 `/actuator/health` 依然是 UP，滚动发布和自动扩缩容都会照常放行。

```yaml
chaos:
  mq:
    outbox:
      health:
        enabled: true                # 默认 true
        pending-threshold: 10000     # 超过判定为 DOWN
        dead-letter-threshold: 100
```

存在 Actuator 时出现在 `/actuator/health` 的 `chaosMqOutbox` 下：

```json
{ "status": "DOWN", "details": { "pending": 20431, "deadLetter": 3, "pendingThreshold": 10000 } }
```

仓储未实现 `countByStatus` 时状态为 `UNKNOWN` 而不是 `UP`——把"查不到"渲染成"积压为 0"比没有健康检查更危险。

## 自定义实现

业务可以注册自己的 `ChaosMetrics` Bean 覆盖框架默认实现（例如接到自研埋点 SDK）：

```java
@Bean
ChaosMetrics chaosMetrics(MyTelemetry telemetry) {
    return (name, tags) -> telemetry.count(name, tags);
}
```

实现必须满足：调用方在请求路径上同步调用，因此不得阻塞、不得抛异常。
