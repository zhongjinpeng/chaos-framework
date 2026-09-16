package com.michael.chaos.core.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 记录所有埋点调用的测试替身。
 */
public class RecordingChaosMetrics implements ChaosMetrics {

    private final List<String> recorded = new ArrayList<>();

    @Override
    public synchronized void increment(String name, String... tags) {
        recorded.add(tags.length == 0 ? name : name + "{" + String.join(",", tags) + "}");
    }

    /**
     * 返回已记录的指标（格式 {@code name{k1,v1,k2,v2}}）。
     */
    public synchronized List<String> recorded() {
        return List.copyOf(recorded);
    }

    /**
     * 返回已记录的指标名（去掉标签）。
     */
    public synchronized List<String> names() {
        return recorded.stream().map(entry -> entry.split("\\{")[0]).collect(Collectors.toList());
    }
}
