package com.michael.chaos.trace;

import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * One request's monotonic timing accumulator.
 *
 * <p>The accumulator is safe to share with asynchronous callbacks. Stage names are normalized so
 * callers cannot accidentally create unbounded or log-breaking keys.</p>
 */
public final class RequestTiming {

    /** Exchange/request attribute name used by reactive integrations. */
    public static final String ATTRIBUTE_NAME = RequestTiming.class.getName();

    private static final int MAX_STAGE_NAME_LENGTH = 64;

    private final long startedAtNanos;

    private final Map<String, StageAccumulator> stages = new ConcurrentHashMap<>();

    private RequestTiming(long startedAtNanos) {
        this.startedAtNanos = startedAtNanos;
    }

    /** Start a new timing accumulator using the monotonic clock. */
    public static RequestTiming start() {
        return new RequestTiming(System.nanoTime());
    }

    /** Record one completed stage duration in nanoseconds. */
    public void record(String stageName, long durationNanos) {
        String normalizedName = normalizeStageName(stageName);
        if (normalizedName.isEmpty()) {
            return;
        }
        StageAccumulator accumulator = stages.computeIfAbsent(normalizedName, ignored -> new StageAccumulator());
        accumulator.totalNanos.add(Math.max(0L, durationNanos));
        accumulator.count.increment();
    }

    /** Return an immutable point-in-time snapshot. */
    public Snapshot snapshot() {
        Map<String, StageSnapshot> stageSnapshots = new LinkedHashMap<>();
        stages.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> stageSnapshots.put(entry.getKey(), entry.getValue().snapshot()));
        String slowestStage = stageSnapshots.entrySet().stream()
                .max(Comparator.comparingLong(entry -> entry.getValue().totalNanos()))
                .map(Map.Entry::getKey)
                .orElse("");
        return new Snapshot(
                Math.max(0L, System.nanoTime() - startedAtNanos),
                Collections.unmodifiableMap(new LinkedHashMap<>(stageSnapshots)),
                slowestStage);
    }

    private static String normalizeStageName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return normalized.length() <= MAX_STAGE_NAME_LENGTH
                ? normalized
                : normalized.substring(0, MAX_STAGE_NAME_LENGTH);
    }

    /** Immutable request timing snapshot. */
    public record Snapshot(long totalNanos, Map<String, StageSnapshot> stages, String slowestStage) {

        /** Total request duration in milliseconds. */
        public double totalMillis() {
            return nanosToMillis(totalNanos);
        }

        /** Stage durations formatted as a compact deterministic map for logs. */
        public Map<String, Double> stageMillis() {
            Map<String, Double> values = new LinkedHashMap<>();
            stages.forEach((name, stage) -> values.put(name, nanosToMillis(stage.totalNanos())));
            return values;
        }

        /** Stage invocation counts formatted as a compact deterministic map for logs. */
        public Map<String, Long> stageCounts() {
            Map<String, Long> values = new LinkedHashMap<>();
            stages.forEach((name, stage) -> values.put(name, stage.count()));
            return values;
        }
    }

    /** Immutable accumulated stage value. */
    public record StageSnapshot(long totalNanos, long count) {
    }

    private static double nanosToMillis(long nanos) {
        return Math.round((nanos / 1_000_000.0D) * 1_000.0D) / 1_000.0D;
    }

    private static final class StageAccumulator {

        private final LongAdder totalNanos = new LongAdder();

        private final LongAdder count = new LongAdder();

        private StageSnapshot snapshot() {
            return new StageSnapshot(totalNanos.sum(), count.sum());
        }
    }
}
