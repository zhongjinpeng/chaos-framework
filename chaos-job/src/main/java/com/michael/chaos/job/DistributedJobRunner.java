package com.michael.chaos.job;

import com.michael.chaos.core.lock.DistributedLock;
import com.michael.chaos.core.lock.LockHandle;
import com.michael.chaos.trace.log.MdcSupport;
import com.michael.chaos.trace.TraceContext;
import com.michael.chaos.trace.TraceContextSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 带分布式互斥和执行上下文的任务执行器。
 *
 * <p>Spring {@code @Scheduled} 在每个实例上都会触发，N 个实例会执行 N 次。在调度方法里通过该执行器运行任务：</p>
 * <pre>{@code
 * @Scheduled(cron = "0 0/5 * * * ?")
 * public void closeTimeoutOrders() {
 *     jobRunner.run("order-close-timeout", context -> orderService.closeTimeoutOrders());
 * }
 * }</pre>
 *
 * <p>执行流程：</p>
 * <ol>
 *     <li>以 {@code key-prefix + jobName} 抢锁，不等待；抢不到说明其他实例正在执行，本次跳过；</li>
 *     <li>为本次执行生成独立 traceId，并在 MDC 写入 {@code jobName}，保证日志可串联；</li>
 *     <li>执行结束后释放锁并恢复线程原有上下文，避免调度线程复用时串用上一次的 trace。</li>
 * </ol>
 *
 * <p>未配置 {@link DistributedLock} 时退化为本地执行并在启动时告警，多实例部署会重复执行。</p>
 */
public class DistributedJobRunner {

    /**
     * MDC 中的任务名 key。
     */
    public static final String MDC_JOB_NAME = "jobName";

    private static final Logger log = LoggerFactory.getLogger(DistributedJobRunner.class);

    private final DistributedLock distributedLock;

    private final String keyPrefix;

    private final Duration leaseTime;

    private final String appName;

    /**
     * 创建任务执行器。
     *
     * @param distributedLock 分布式锁；为 {@code null} 时本地执行
     * @param keyPrefix 锁 key 前缀
     * @param leaseTime 锁租约；{@code null} 时启用锁实现的自动续期（Redisson watchdog）
     * @param appName 应用名，写入执行上下文
     */
    public DistributedJobRunner(DistributedLock distributedLock, String keyPrefix, Duration leaseTime, String appName) {
        this.distributedLock = distributedLock;
        this.keyPrefix = keyPrefix == null ? "chaos:job:" : keyPrefix;
        this.leaseTime = leaseTime;
        this.appName = appName == null ? "" : appName;
        if (distributedLock == null) {
            log.warn("No DistributedLock bean found, scheduled jobs run without mutual exclusion and will be executed "
                    + "once per instance in multi-instance deployments");
        }
    }

    /**
     * 执行无参数任务。
     *
     * @return 本实例实际执行了任务返回 {@code true}，因其他实例持锁而跳过返回 {@code false}
     */
    public boolean run(String jobName, JobExecutor executor) {
        return run(jobName, Map.of(), executor);
    }

    /**
     * 执行带参数任务。
     *
     * @return 本实例实际执行了任务返回 {@code true}，因其他实例持锁而跳过返回 {@code false}
     */
    public boolean run(String jobName, Map<String, String> parameters, JobExecutor executor) {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("jobName must not be blank");
        }
        Objects.requireNonNull(executor, "executor must not be null");
        LockHandle handle = null;
        if (distributedLock != null) {
            Optional<LockHandle> acquired = distributedLock.tryLock(keyPrefix + jobName, Duration.ZERO, leaseTime);
            if (acquired.isEmpty()) {
                log.debug("Job skipped because another instance holds the lock, jobName={}", jobName);
                return false;
            }
            handle = acquired.get();
        }
        TraceContextSnapshot previous = TraceContext.capture();
        try {
            TraceContext.start(null, null, null, null, appName);
            MdcSupport.putIfNotBlank(MDC_JOB_NAME, jobName);
            JobExecutionContext context = new JobExecutionContext(jobName, TraceContext.traceId(), parameters, Instant.now());
            long start = System.nanoTime();
            log.info("Job started, jobName={}, executionId={}", jobName, context.executionId());
            executor.execute(context);
            log.info("Job finished, jobName={}, costMs={}", jobName, (System.nanoTime() - start) / 1_000_000);
            return true;
        } catch (RuntimeException ex) {
            log.error("Job failed, jobName={}", jobName, ex);
            throw ex;
        } finally {
            if (handle != null) {
                handle.unlock();
            }
            // 恢复进入前的 RequestContext 与 MDC（包括移除 jobName）。
            TraceContext.restore(previous);
        }
    }
}
