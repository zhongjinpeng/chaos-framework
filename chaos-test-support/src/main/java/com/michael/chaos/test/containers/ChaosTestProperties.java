package com.michael.chaos.test.containers;

import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * 把外部依赖的连接信息注册为 Spring 属性。
 *
 * <p>与 {@code @DynamicPropertySource} 配合使用；值以 {@link Supplier} 形式延迟求值，容器真正启动后才读取端口。
 * 本类不引用 Testcontainers 类型，也可以用于连接 CI 中预先准备好的 Redis / 数据库。</p>
 *
 * <pre>{@code
 * @DynamicPropertySource
 * static void properties(DynamicPropertyRegistry registry) {
 *     ChaosContainers.registerRedis(registry, REDIS);
 *     ChaosContainers.registerDataSource(registry, MYSQL);
 * }
 * }</pre>
 */
public final class ChaosTestProperties {

    /**
     * Spring Data Redis 主机属性；Redisson Spring Boot Starter 同样读取该属性。
     */
    public static final String REDIS_HOST = "spring.data.redis.host";

    /**
     * Spring Data Redis 端口属性。
     */
    public static final String REDIS_PORT = "spring.data.redis.port";

    /**
     * 数据源 URL 属性。
     */
    public static final String DATASOURCE_URL = "spring.datasource.url";

    /**
     * 数据源用户名属性。
     */
    public static final String DATASOURCE_USERNAME = "spring.datasource.username";

    /**
     * 数据源密码属性。
     */
    public static final String DATASOURCE_PASSWORD = "spring.datasource.password";

    /**
     * 数据源驱动属性。
     */
    public static final String DATASOURCE_DRIVER = "spring.datasource.driver-class-name";

    private ChaosTestProperties() {
    }

    /**
     * 注册 Redis 连接属性。
     */
    public static void registerRedis(DynamicPropertyRegistry registry, Supplier<String> host, Supplier<Integer> port) {
        Objects.requireNonNull(registry, "registry must not be null");
        registry.add(REDIS_HOST, host::get);
        registry.add(REDIS_PORT, port::get);
    }

    /**
     * 注册 JDBC 数据源属性。
     */
    public static void registerDataSource(
            DynamicPropertyRegistry registry,
            Supplier<String> url,
            Supplier<String> username,
            Supplier<String> password,
            Supplier<String> driverClassName) {
        Objects.requireNonNull(registry, "registry must not be null");
        registry.add(DATASOURCE_URL, url::get);
        registry.add(DATASOURCE_USERNAME, username::get);
        registry.add(DATASOURCE_PASSWORD, password::get);
        registry.add(DATASOURCE_DRIVER, driverClassName::get);
    }
}
