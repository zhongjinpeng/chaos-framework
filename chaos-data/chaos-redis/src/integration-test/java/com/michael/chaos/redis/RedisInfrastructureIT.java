package com.michael.chaos.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.idempotent.IdempotentRecord;
import com.michael.chaos.redis.idempotent.RedissonIdempotentRecordStore;
import com.michael.chaos.redis.idempotent.RedissonIdempotentRepository;
import com.michael.chaos.redis.lock.RedissonDistributedLock;
import com.michael.chaos.core.ratelimit.RateLimitContext;
import com.michael.chaos.redis.lua.LuaScriptExecutor;
import com.michael.chaos.redis.ratelimit.RedissonRateLimiter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis 真实基础设施集成测试。
 */
@Testcontainers
class RedisInfrastructureIT {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private static RedissonClient redissonClient;

    /**
     * 初始化 Redisson 客户端。
     */
    @BeforeAll
    static void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redissonClient = Redisson.create(config);
    }

    /**
     * 关闭 Redisson 客户端。
     */
    @AfterAll
    static void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    /**
     * Redisson 分布式锁应能在真实 Redis 上加锁和释放。
     */
    @Test
    void shouldAcquireAndReleaseDistributedLock() {
        RedissonDistributedLock lock = new RedissonDistributedLock(redissonClient);

        var handle = lock.tryLock("it:lock:order", Duration.ofSeconds(1), Duration.ofSeconds(10));

        assertThat(handle).isPresent();
        assertThat(redissonClient.getLock("it:lock:order").isLocked()).isTrue();

        handle.get().unlock();

        assertThat(redissonClient.getLock("it:lock:order").isLocked()).isFalse();
    }

    /**
     * 幂等存储应保证同一个 key 在 TTL 内只写入一次。
     */
    @Test
    void shouldSaveIdempotentKeyOnlyOnce() {
        RedissonIdempotentRepository repository = new RedissonIdempotentRepository(redissonClient);

        assertThat(repository.saveIfAbsent("it:idempotent:order-1", Duration.ofMinutes(1))).isTrue();
        assertThat(repository.saveIfAbsent("it:idempotent:order-1", Duration.ofMinutes(1))).isFalse();
    }

    /**
     * Lua 执行器应能在真实 Redis 上执行原子脚本。
     */
    @Test
    void shouldExecuteLuaScript() {
        LuaScriptExecutor executor = new LuaScriptExecutor(redissonClient);

        Long result = executor.eval(
                "return redis.call('incr', KEYS[1])",
                RScript.ReturnType.INTEGER,
                List.of("it:lua:counter")
        );

        assertThat(result).isEqualTo(1L);
    }

    /**
     * 幂等 key 删除后应允许再次写入。
     */
    @Test
    void shouldRemoveIdempotentKey() {
        RedissonIdempotentRepository repository = new RedissonIdempotentRepository(redissonClient);

        assertThat(repository.saveIfAbsent("it:idempotent:order-2", Duration.ofMinutes(1))).isTrue();
        repository.remove("it:idempotent:order-2");

        assertThat(repository.saveIfAbsent("it:idempotent:order-2", Duration.ofMinutes(1))).isTrue();
    }

    /**
     * Lua 滑动窗口限流在真实 Redis 上应只放行配置的许可数。
     */
    @Test
    void shouldLimitRequestsWithSlidingWindow() {
        RedissonRateLimiter limiter = new RedissonRateLimiter(redissonClient);
        RateLimitContext context = new RateLimitContext("it:" + System.nanoTime(), 5);

        long allowed = java.util.stream.IntStream.range(0, 20)
                .filter(i -> limiter.tryAcquire(context))
                .count();

        assertThat(allowed).isBetween(5L, 10L);
    }

    /**
     * 响应快照要能跨实例完整还原：状态码、内容类型、响应头和含中文的响应体都不能丢。
     *
     * <p>这里用两个独立的 store 实例读写，等价于"实例 A 处理首次请求、实例 B 收到重复请求"。</p>
     */
    @Test
    void shouldReplayResponseRecordAcrossInstances() {
        RedissonIdempotentRecordStore writer = new RedissonIdempotentRecordStore(redissonClient);
        RedissonIdempotentRecordStore reader = new RedissonIdempotentRecordStore(redissonClient);
        IdempotentRecord record = new IdempotentRecord(
                201,
                "application/json;charset=UTF-8",
                "{\"id\":1,\"name\":\"订单\"}".getBytes(StandardCharsets.UTF_8),
                Map.of("Location", "/orders/1"),
                1_700_000_000_000L);

        writer.save("it:idem:order-3", record, Duration.ofMinutes(1));

        IdempotentRecord replayed = reader.find("it:idem:order-3").orElseThrow();
        assertThat(replayed.statusCode()).isEqualTo(201);
        assertThat(replayed.contentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(replayed.headers()).containsEntry("Location", "/orders/1");
        assertThat(new String(replayed.body(), StandardCharsets.UTF_8)).contains("订单");
    }

    /**
     * 快照与占位使用不同的 Redis key：释放占位不能把快照一起删掉。
     */
    @Test
    void shouldKeepRecordWhenIdempotentKeyIsReleased() {
        RedissonIdempotentRepository repository = new RedissonIdempotentRepository(redissonClient);
        RedissonIdempotentRecordStore store = new RedissonIdempotentRecordStore(redissonClient);
        IdempotentRecord record = new IdempotentRecord(
                200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8), Map.of(), 1L);
        repository.saveIfAbsent("it:idem:order-4", Duration.ofMinutes(1));
        store.save("it:idem:order-4", record, Duration.ofMinutes(1));

        repository.remove("it:idem:order-4");

        assertThat(store.find("it:idem:order-4")).isPresent();
    }

    /**
     * 快照必须随 TTL 自动过期，否则响应体会在 Redis 里无限堆积。
     */
    @Test
    void shouldExpireResponseRecord() throws InterruptedException {
        RedissonIdempotentRecordStore store = new RedissonIdempotentRecordStore(redissonClient);
        store.save("it:idem:order-5",
                new IdempotentRecord(200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8), Map.of(), 1L),
                Duration.ofMillis(300));

        Thread.sleep(700);

        assertThat(store.find("it:idem:order-5")).isEmpty();
    }
}
