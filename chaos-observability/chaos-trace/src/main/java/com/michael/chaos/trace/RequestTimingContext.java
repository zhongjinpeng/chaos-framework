package com.michael.chaos.trace;

import com.michael.chaos.core.context.ContextAccessor;
import com.michael.chaos.core.context.ContextPropagation;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Request timing context shared by framework filters and business infrastructure adapters.
 *
 * <p>The current accumulator participates in Chaos context propagation. Nested scopes using the
 * same stage name are collapsed so wrapper layers do not double count the same dependency call.</p>
 */
public final class RequestTimingContext {

    private static final ThreadLocal<RequestTiming> CURRENT = new ThreadLocal<>();

    private static final ThreadLocal<Map<String, Integer>> ACTIVE_STAGE_DEPTHS =
            ThreadLocal.withInitial(HashMap::new);

    private static final ContextAccessor<RequestTiming> ACCESSOR = new ContextAccessor<>() {
        @Override
        public RequestTiming capture() {
            return CURRENT.get();
        }

        @Override
        public ContextAccessor.Scope restore(RequestTiming snapshot) {
            RequestTimingContext.Scope scope = RequestTimingContext.open(snapshot);
            return scope::close;
        }

        /**
         * 请求边界清理时彻底移除 ThreadLocal，避免线程池线程残留空 Map。
         */
        @Override
        public void clear() {
            CURRENT.remove();
            ACTIVE_STAGE_DEPTHS.remove();
        }
    };

    static {
        ContextPropagation.register(ACCESSOR);
    }

    private RequestTimingContext() {
    }

    /** Bind an accumulator to the current execution context. */
    public static Scope open(RequestTiming timing) {
        RequestTiming previous = CURRENT.get();
        Map<String, Integer> previousDepths = ACTIVE_STAGE_DEPTHS.get();
        if (timing == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(timing);
        }
        ACTIVE_STAGE_DEPTHS.set(new HashMap<>());
        return new Scope(previous, previousDepths);
    }

    /** Return the currently bound accumulator. */
    public static Optional<RequestTiming> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Start a dependency or business stage measured with the monotonic clock. */
    public static StageScope stage(String stageName) {
        RequestTiming timing = CURRENT.get();
        if (timing == null || stageName == null || stageName.isBlank()) {
            return StageScope.noop();
        }
        Map<String, Integer> depths = ACTIVE_STAGE_DEPTHS.get();
        int depth = depths.getOrDefault(stageName, 0);
        depths.put(stageName, depth + 1);
        return new StageScope(timing, stageName, System.nanoTime(), depth == 0);
    }

    /** Run a value-returning operation as one timing stage. */
    public static <T> T call(String stageName, ThrowingSupplier<T> operation) throws Throwable {
        Objects.requireNonNull(operation, "operation must not be null");
        try (StageScope ignored = stage(stageName)) {
            return operation.get();
        }
    }

    /** Run a void operation as one timing stage. */
    public static void run(String stageName, ThrowingRunnable operation) throws Throwable {
        Objects.requireNonNull(operation, "operation must not be null");
        try (StageScope ignored = stage(stageName)) {
            operation.run();
        }
    }

    /** Scope that restores the previously bound timing context. */
    public static final class Scope implements AutoCloseable {

        private final RequestTiming previous;

        private final Map<String, Integer> previousDepths;

        private boolean closed;

        private Scope(RequestTiming previous, Map<String, Integer> previousDepths) {
            this.previous = previous;
            this.previousDepths = previousDepths;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
            if (previousDepths == null || previousDepths.isEmpty()) {
                ACTIVE_STAGE_DEPTHS.remove();
            } else {
                ACTIVE_STAGE_DEPTHS.set(previousDepths);
            }
        }
    }

    /** Auto-closeable timing stage. */
    public static final class StageScope implements AutoCloseable {

        private static final StageScope NOOP = new StageScope(null, "", 0L, false);

        private final RequestTiming timing;

        private final String stageName;

        private final long startedAtNanos;

        private final boolean recordDuration;

        private boolean closed;

        private StageScope(
                RequestTiming timing,
                String stageName,
                long startedAtNanos,
                boolean recordDuration) {
            this.timing = timing;
            this.stageName = stageName;
            this.startedAtNanos = startedAtNanos;
            this.recordDuration = recordDuration;
        }

        private static StageScope noop() {
            return NOOP;
        }

        @Override
        public void close() {
            if (closed || timing == null) {
                return;
            }
            closed = true;
            Map<String, Integer> depths = ACTIVE_STAGE_DEPTHS.get();
            int remainingDepth = Math.max(0, depths.getOrDefault(stageName, 1) - 1);
            if (remainingDepth == 0) {
                depths.remove(stageName);
            } else {
                depths.put(stageName, remainingDepth);
            }
            if (recordDuration) {
                timing.record(stageName, System.nanoTime() - startedAtNanos);
            }
        }
    }

    /** Supplier variant that preserves checked exceptions from infrastructure APIs. */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    /** Runnable variant that preserves checked exceptions from infrastructure APIs. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
