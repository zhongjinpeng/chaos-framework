package com.michael.chaos.core.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 统一上下文传播测试。
 */
class ContextPropagationTest {

    private static final ThreadLocal<String> VALUE = new ThreadLocal<>();

    private static final ThreadLocal<String> FAILING_VALUE = new ThreadLocal<>();

    /**
     * 包装后的任务在执行线程恢复提交线程上下文，结束后还原执行线程原上下文。
     */
    @Test
    void wrapShouldRestoreAndThenRevertContext() {
        ContextPropagation.register(new ValueAccessor());
        VALUE.set("submitter");
        Runnable wrapped = ContextPropagation.wrap(() -> assertEquals("submitter", VALUE.get()));
        VALUE.set("worker");

        wrapped.run();

        assertEquals("worker", VALUE.get());
        VALUE.remove();
    }

    /**
     * 某个访问器 restore 失败时，已恢复的作用域必须被关闭，不能残留半份上下文。
     */
    @Test
    void restoreFailureShouldCloseAlreadyRestoredScopes() {
        ValueAccessor valueAccessor = new ValueAccessor();
        FailingAccessor failingAccessor = new FailingAccessor();
        ContextSnapshot snapshot = new ContextSnapshot(
                new ContextAccessor<?>[]{valueAccessor, failingAccessor},
                new Object[]{"captured", "boom"}
        );
        VALUE.set("original");

        assertThrows(IllegalStateException.class, () -> ContextPropagation.restore(snapshot));

        assertEquals("original", VALUE.get());
        assertNull(FAILING_VALUE.get());
        VALUE.remove();
    }

    /**
     * clearAll 应清理所有已注册访问器的线程上下文。
     */
    @Test
    void clearAllShouldClearRegisteredContexts() {
        ContextPropagation.register(new ValueAccessor());
        VALUE.set("leaked");

        ContextPropagation.clearAll();

        assertNull(VALUE.get());
    }

    /**
     * 并发注册与捕获不应出现数组越界。
     */
    @Test
    void captureShouldBeSafeUnderConcurrentRegistration() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < 2_000; j++) {
                        ContextPropagation.capture();
                    }
                    return null;
                }));
            }
            futures.add(executor.submit(() -> {
                start.await();
                for (int j = 0; j < 200; j++) {
                    ContextPropagation.register(new DynamicAccessor() { });
                }
                return null;
            }));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        assertTrue(ContextPropagation.registeredCount() > 0);
    }

    private static class ValueAccessor implements ContextAccessor<String> {

        @Override
        public String capture() {
            return VALUE.get();
        }

        @Override
        public Scope restore(String snapshot) {
            String previous = VALUE.get();
            set(snapshot);
            return () -> set(previous);
        }

        private static void set(String value) {
            if (value == null) {
                VALUE.remove();
            } else {
                VALUE.set(value);
            }
        }
    }

    private static class FailingAccessor implements ContextAccessor<String> {

        @Override
        public String capture() {
            return FAILING_VALUE.get();
        }

        @Override
        public Scope restore(String snapshot) {
            throw new IllegalStateException("restore failed");
        }
    }

    private static class DynamicAccessor implements ContextAccessor<Object> {

        @Override
        public Object capture() {
            return null;
        }

        @Override
        public Scope restore(Object snapshot) {
            return () -> { };
        }
    }
}
