package com.michael.chaos.test.containers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * 动态属性注册测试（不启动容器）。
 */
class ChaosTestPropertiesTest {

    /**
     * Redis 属性应延迟求值：注册时不读取端口，容器启动后才读取。
     */
    @Test
    void redisPropertiesShouldBeLazy() {
        RecordingRegistry registry = new RecordingRegistry();
        AtomicInteger portReads = new AtomicInteger();

        ChaosTestProperties.registerRedis(registry, () -> "localhost", () -> {
            portReads.incrementAndGet();
            return 16379;
        });

        assertThat(portReads).hasValue(0);
        assertThat(registry.values).containsOnlyKeys(ChaosTestProperties.REDIS_HOST, ChaosTestProperties.REDIS_PORT);
        assertThat(registry.values.get(ChaosTestProperties.REDIS_PORT).get()).isEqualTo(16379);
    }

    /**
     * 数据源属性应注册 URL、用户名、密码和驱动。
     */
    @Test
    void dataSourcePropertiesShouldBeRegistered() {
        RecordingRegistry registry = new RecordingRegistry();

        ChaosTestProperties.registerDataSource(
                registry, () -> "jdbc:mysql://h:3306/db", () -> "u", () -> "p", () -> "com.mysql.cj.jdbc.Driver");

        assertThat(registry.values).containsOnlyKeys(
                ChaosTestProperties.DATASOURCE_URL,
                ChaosTestProperties.DATASOURCE_USERNAME,
                ChaosTestProperties.DATASOURCE_PASSWORD,
                ChaosTestProperties.DATASOURCE_DRIVER);
        assertThat(registry.values.get(ChaosTestProperties.DATASOURCE_URL).get()).isEqualTo("jdbc:mysql://h:3306/db");
    }

    private static final class RecordingRegistry implements DynamicPropertyRegistry {

        private final Map<String, Supplier<Object>> values = new LinkedHashMap<>();

        @Override
        public void add(String name, Supplier<Object> valueSupplier) {
            values.put(name, valueSupplier);
        }
    }
}
