package com.michael.chaos.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.trace.TraceContext;
import com.michael.chaos.core.lock.DistributedLock;
import com.michael.chaos.core.lock.LockHandle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * 分布式任务执行器测试。
 */
class DistributedJobRunnerTest {

    private final DistributedLock lock = mock(DistributedLock.class);

    /**
     * 抢不到锁时跳过执行。
     */
    @Test
    void shouldSkipWhenLockHeldByAnotherInstance() {
        when(lock.tryLock(eq("chaos:job:close"), eq(Duration.ZERO), any())).thenReturn(Optional.empty());
        List<String> executed = new ArrayList<>();

        boolean ran = new DistributedJobRunner(lock, "chaos:job:", null, "order").run("close", ctx -> executed.add("x"));

        assertThat(ran).isFalse();
        assertThat(executed).isEmpty();
    }

    /**
     * 抢到锁时应在独立上下文中执行，结束后释放锁并清理上下文。
     */
    @Test
    void shouldRunWithContextAndReleaseLock() {
        LockHandle handle = mock(LockHandle.class);
        when(lock.tryLock(eq("chaos:job:close"), eq(Duration.ZERO), any())).thenReturn(Optional.of(handle));
        List<String> observed = new ArrayList<>();

        boolean ran = new DistributedJobRunner(lock, "chaos:job:", null, "order").run("close", ctx -> {
            observed.add(ctx.executionId());
            observed.add(TraceContext.traceId());
            observed.add(MDC.get(DistributedJobRunner.MDC_JOB_NAME));
        });

        assertThat(ran).isTrue();
        assertThat(observed.get(0)).isNotBlank().isEqualTo(observed.get(1));
        assertThat(observed.get(2)).isEqualTo("close");
        verify(handle).unlock();
        assertThat(RequestContext.current()).isEmpty();
        assertThat(MDC.get(DistributedJobRunner.MDC_JOB_NAME)).isNull();
    }

    /**
     * 任务异常时同样释放锁。
     */
    @Test
    void shouldReleaseLockWhenJobFails() {
        LockHandle handle = mock(LockHandle.class);
        when(lock.tryLock(any(), any(), any())).thenReturn(Optional.of(handle));
        DistributedJobRunner runner = new DistributedJobRunner(lock, "chaos:job:", Duration.ofMinutes(1), "order");

        assertThatThrownBy(() -> runner.run("close", ctx -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        verify(handle).unlock();
    }

    /**
     * 没有分布式锁时本地执行。
     */
    @Test
    void shouldRunLocallyWithoutLock() {
        List<String> executed = new ArrayList<>();

        assertThat(new DistributedJobRunner(null, null, null, null).run("close", ctx -> executed.add("x"))).isTrue();
        assertThat(executed).containsExactly("x");
    }
}
