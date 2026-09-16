# 分布式锁（chaos-core + chaos-redis）

> 仓库中不存在 `chaos-lock` 模块。锁接口位于 `chaos-core`（包 `com.michael.chaos.core.lock`），Redisson 实现位于 `chaos-redis`。

## 职责

- `DistributedLock`：`tryLock(key, waitTime, leaseTime)` 与 `execute(...)`；
- `LockHandle`：锁句柄，支持 try-with-resources，重复释放安全；
- `RedissonDistributedLock`：Redisson 实现。

## 依赖方式

```xml
<!-- 只依赖接口 -->
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-core</artifactId>
</dependency>
<!-- 运行时实现 -->
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-redis-starter</artifactId>
</dependency>
```

## 示例

```java
// 固定租约：租约必须大于业务最长耗时
distributedLock.execute("order:" + id, Duration.ofSeconds(1), Duration.ofSeconds(30), () -> submit(id));

// 耗时不可预估：leaseTime 传 null，启用 Redisson watchdog 自动续期
try (LockHandle handle = distributedLock.tryLock("report:daily", Duration.ZERO, null).orElseThrow()) {
    generateReport();
}
```

## 注意事项

- 显式租约到期后锁会被自动释放，其他节点可能进入临界区；Redisson 实现会在释放时输出告警，`execute` 输出 ERROR。
- 锁 key 受 `chaos.redis.key-prefix` 影响，修改前缀的发布窗口内新旧实例不互斥。
- 定时任务互斥请使用 `DistributedJobRunner`，见 [chaos-job](../modules/chaos-job.md)。
