# chaos-job

## 职责

定时任务执行支持：

- `JobExecutor` / `JobExecutionContext`：任务执行端口与上下文；
- `DistributedJobRunner`：带分布式互斥、独立 traceId 和 MDC 任务名的执行器；
- `chaos-autoconfigure`（job）：开启 Spring Scheduling 并注册 `DistributedJobRunner`。

## 依赖方式

```xml
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-job-starter</artifactId>
</dependency>
<!-- 多实例部署需要分布式锁 -->
<dependency>
    <groupId>com.michael</groupId>
    <artifactId>chaos-redis-starter</artifactId>
</dependency>
```

## 配置

```yaml
chaos:
  job:
    enabled: true              # 总开关
    scheduling-enabled: true   # 是否开启 @EnableScheduling；使用 XXL-JOB 等外部调度时可关闭
    lock:
      key-prefix: "chaos:job:" # 锁 key = key-prefix + jobName（Redis 实现还会叠加 chaos.redis.key-prefix）
      lease-time:              # 为空时使用 Redisson watchdog 自动续期；显式设置必须大于任务最长耗时
```

## 示例

```java
@Component
public class OrderTimeoutJob {

    private final DistributedJobRunner jobRunner;
    private final OrderService orderService;

    @Scheduled(cron = "0 0/5 * * * ?")
    public void closeTimeoutOrders() {
        jobRunner.run("order-close-timeout", context -> {
            // context.executionId() 与日志 traceId 一致
            orderService.closeTimeoutOrders(context.fireTime());
        });
    }
}
```

执行流程：

1. 以 `key-prefix + jobName` 抢锁，**不等待**；抢不到说明其他实例正在执行，本次跳过并返回 `false`；
2. 生成独立 traceId 写入 `RequestContext` 和 MDC，并写入 MDC `jobName`；
3. 记录开始、结束耗时和异常日志；
4. 结束后释放锁并恢复调度线程原有上下文。

## 注意事项

- 仅 `@Scheduled` 不能防止多实例重复执行，必须通过 `DistributedJobRunner` 或其他分布式调度。
- 容器中没有 `DistributedLock` Bean 时，执行器退化为本地执行并在启动时输出 WARN。
- 任务内访问多租户表时，需要显式设置租户上下文或只访问忽略租户的表；MyBatis 默认 `missing-tenant-behavior=deny`。
- 显式配置 `lease-time` 时，任务超时后锁会被自动释放，其他实例可能并发执行；释放时框架会输出告警。
