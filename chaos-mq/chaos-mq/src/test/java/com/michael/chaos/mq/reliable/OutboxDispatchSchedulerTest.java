package com.michael.chaos.mq.reliable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * outbox 调度器测试。
 */
class OutboxDispatchSchedulerTest {

    private final ReliableMessageDispatcher dispatcher = mock(ReliableMessageDispatcher.class);

    /**
     * 满批时应连续派发，直到不足一批或达到轮数上限。
     */
    @Test
    void shouldKeepDispatchingWhileBatchIsFull() {
        when(dispatcher.dispatchDueMessages(anyInt())).thenReturn(10, 10, 3);
        OutboxDispatchScheduler scheduler = new OutboxDispatchScheduler(dispatcher, settings());

        scheduler.dispatchSafely();

        verify(dispatcher, times(3)).dispatchDueMessages(10);
    }

    /**
     * 派发异常不能向外抛出，否则调度线程会终止。
     */
    @Test
    void shouldSwallowDispatchFailure() {
        when(dispatcher.dispatchDueMessages(anyInt())).thenThrow(new IllegalStateException("table not found"));
        OutboxDispatchScheduler scheduler = new OutboxDispatchScheduler(dispatcher, settings());

        assertThatCode(scheduler::dispatchSafely).doesNotThrowAnyException();
        assertThatCode(scheduler::cleanupSafely).doesNotThrowAnyException();
    }

    /**
     * 启动和停止应幂等。
     */
    @Test
    void shouldStartAndStop() {
        OutboxDispatchScheduler scheduler = new OutboxDispatchScheduler(dispatcher, settings());

        scheduler.start();
        scheduler.start();
        assertThat(scheduler.isRunning()).isTrue();
        scheduler.stop();
        scheduler.stop();
        assertThat(scheduler.isRunning()).isFalse();
    }

    private static OutboxDispatchScheduler.Settings settings() {
        return new OutboxDispatchScheduler.Settings(Duration.ofHours(1), Duration.ofSeconds(1), 10, 5,
                true, Duration.ofHours(1), Duration.ofDays(7), 100);
    }
}
