package com.michael.chaos.core.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 指标名约定测试。
 *
 * <p>指标名一旦发布就是监控面板和告警规则的契约，改名等于让所有下游看板静默失效。
 * 这里锁住命名约定（{@code chaos.} 前缀、点分小写）并保证没有重名。</p>
 */
class ChaosMeterNamesTest {

    private static List<Field> constants() {
        List<Field> fields = new ArrayList<>();
        for (Field field : ChaosMeterNames.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) {
                fields.add(field);
            }
        }
        return fields;
    }

    @Test
    void shouldFollowMicrometerNamingConvention() throws Exception {
        Set<String> seen = new HashSet<>();
        for (Field field : constants()) {
            String value = (String) field.get(null);
            if (field.getName().startsWith("TAG_")) {
                assertTrue(value.matches("[a-z][a-z-]*"), field.getName() + " 标签名必须是小写短横线：" + value);
                continue;
            }
            assertTrue(value.matches("[a-z][a-z0-9.]*[a-z0-9]"),
                    field.getName() + " 指标名必须是点分小写：" + value);
            assertTrue(value.startsWith("chaos.") || value.equals("http.server.requests"),
                    field.getName() + " 指标名必须带 chaos. 前缀：" + value);
            assertTrue(seen.add(value), "指标名重复：" + value);
        }
        assertFalse(seen.isEmpty());
    }

    @Test
    void noopShouldSwallowEverything() {
        ChaosMetrics metrics = NoopChaosMetrics.instance();

        metrics.increment(ChaosMeterNames.RATE_LIMIT_REJECTED, ChaosMeterNames.TAG_SOURCE, "web");

        assertEquals(NoopChaosMetrics.instance(), metrics, "空实现应为共享单例，避免每个组件各建一个");
    }
}
