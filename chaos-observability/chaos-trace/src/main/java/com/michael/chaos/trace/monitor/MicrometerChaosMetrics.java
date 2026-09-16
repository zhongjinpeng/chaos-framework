package com.michael.chaos.trace.monitor;

import com.michael.chaos.core.metrics.ChaosMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Micrometer 的治理指标实现。
 *
 * <p>计数器按"指标名 + 标签"缓存：{@code MeterRegistry.counter(...)} 每次调用都要构造 Tags 并查注册表，
 * 而这些调用位于限流、幂等等请求路径上，缓存后热点路径只剩一次 map 查找和一次原子自增。</p>
 *
 * <p>上报失败（标签非法、注册表已关闭）只记一次 debug 日志后吞掉：埋点不能让业务请求失败。</p>
 */
public class MicrometerChaosMetrics implements ChaosMetrics {

    private static final Logger log = LoggerFactory.getLogger(MicrometerChaosMetrics.class);

    private final MeterRegistry registry;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /**
     * 创建指标实现。
     */
    public MicrometerChaosMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void increment(String name, String... tags) {
        try {
            counters.computeIfAbsent(cacheKey(name, tags),
                    key -> Counter.builder(name).tags(Tags.of(tags)).register(registry)).increment();
        } catch (RuntimeException ex) {
            log.debug("Failed to record chaos metric {}: {}", name, ex.getMessage());
        }
    }

    /**
     * 标签已经是低基数枚举值，直接拼接即可作为缓存 key。
     */
    private static String cacheKey(String name, String... tags) {
        if (tags == null || tags.length == 0) {
            return name;
        }
        StringBuilder key = new StringBuilder(name);
        for (String tag : tags) {
            key.append('|').append(tag);
        }
        return key.toString();
    }
}
