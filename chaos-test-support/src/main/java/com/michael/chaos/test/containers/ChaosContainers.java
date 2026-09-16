package com.michael.chaos.test.containers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 框架集成测试常用的 Testcontainers 工厂。
 *
 * <p>镜像版本在这里统一固定，避免各服务的集成测试各自使用 {@code latest} 导致结果随上游镜像漂移。
 * 按仓库约定，Testcontainers 只应出现在 {@code src/integration-test/java} 的 {@code *IT} 中，
 * 通过 {@code -Pchaos-integration-test} 运行，普通单测不启动 Docker。</p>
 *
 * <pre>{@code
 * @Testcontainers
 * class OrderRepositoryIT {
 *
 *     @Container
 *     static final MySQLContainer<?> MYSQL = ChaosContainers.mysql();
 *
 *     @DynamicPropertySource
 *     static void properties(DynamicPropertyRegistry registry) {
 *         ChaosContainers.registerDataSource(registry, MYSQL);
 *     }
 * }
 * }</pre>
 *
 * <p>需要项目 test classpath 中存在 {@code org.testcontainers:testcontainers}；MySQL / PostgreSQL 工厂还分别需要
 * {@code org.testcontainers:mysql} / {@code org.testcontainers:postgresql}。</p>
 */
public final class ChaosContainers {

    /**
     * Redis 镜像。
     */
    public static final String REDIS_IMAGE = "redis:7.4-alpine";

    /**
     * MySQL 镜像。
     */
    public static final String MYSQL_IMAGE = "mysql:8.4";

    /**
     * PostgreSQL 镜像。
     */
    public static final String POSTGRESQL_IMAGE = "postgres:16-alpine";

    private static final int REDIS_PORT = 6379;

    private ChaosContainers() {
    }

    /**
     * 创建未启动的 Redis 容器。
     */
    @SuppressWarnings("resource")
    public static GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(REDIS_PORT);
    }

    /**
     * 创建未启动的 MySQL 容器。
     */
    @SuppressWarnings("resource")
    public static MySQLContainer<?> mysql() {
        return new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE).asCompatibleSubstituteFor("mysql"));
    }

    /**
     * 创建未启动的 PostgreSQL 容器。
     */
    @SuppressWarnings("resource")
    public static PostgreSQLContainer<?> postgresql() {
        return new PostgreSQLContainer<>(DockerImageName.parse(POSTGRESQL_IMAGE).asCompatibleSubstituteFor("postgres"));
    }

    /**
     * 注册 Redis 容器的连接属性。
     */
    public static void registerRedis(DynamicPropertyRegistry registry, GenericContainer<?> redis) {
        ChaosTestProperties.registerRedis(registry, redis::getHost, () -> redis.getMappedPort(REDIS_PORT));
    }

    /**
     * 注册 JDBC 容器的数据源属性。
     */
    public static void registerDataSource(DynamicPropertyRegistry registry, JdbcDatabaseContainer<?> database) {
        ChaosTestProperties.registerDataSource(
                registry,
                database::getJdbcUrl,
                database::getUsername,
                database::getPassword,
                database::getDriverClassName
        );
    }
}
