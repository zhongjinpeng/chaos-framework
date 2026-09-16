package com.michael.chaos.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.michael.chaos.domain.cache.DefaultCacheKeyStrategy;
import com.michael.chaos.core.idempotent.IdempotentRecord;
import com.michael.chaos.core.idempotent.IdempotentRecordCodec;
import com.michael.chaos.core.lock.LockHandle;
import com.michael.chaos.core.ratelimit.RateLimitContext;
import com.michael.chaos.redis.bloom.BloomFilterTemplate;
import com.michael.chaos.redis.idempotent.RedissonIdempotentRecordStore;
import com.michael.chaos.redis.idempotent.RedissonIdempotentRepository;
import com.michael.chaos.redis.key.PrefixedCacheKeyStrategy;
import com.michael.chaos.redis.key.RedisKeyPrefix;
import com.michael.chaos.redis.lock.RedissonDistributedLock;
import com.michael.chaos.redis.ratelimit.RedissonRateLimiter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;

/**
 * Redis 组件单元测试。
 */
class RedisComponentsTest {

    private final RedissonClient client = mock(RedissonClient.class);

    /**
     * 幂等 remove 必须真正删除 Redis key，并带上配置的前缀。
     */
    @Test
    @SuppressWarnings("unchecked")
    void idempotentRemoveShouldDeleteBucket() {
        RBucket<Object> bucket = mock(RBucket.class);
        when(client.getBucket("order-service:mq:topic:1")).thenReturn(bucket);
        RedissonIdempotentRepository repository = new RedissonIdempotentRepository(client, RedisKeyPrefix.of("order-service"));

        repository.remove("mq:topic:1");

        verify(bucket).delete();
    }

    /**
     * 幂等 TTL 非法时应拒绝。
     */
    @Test
    void idempotentShouldRejectInvalidTtl() {
        RedissonIdempotentRepository repository = new RedissonIdempotentRepository(client);

        assertThatThrownBy(() -> repository.saveIfAbsent("k", Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 响应快照与占位 key 必须落在不同的 Redis key 上。
     *
     * <p>共用同一个 key 时，请求失败释放占位会把快照一起删掉；更糟的是成功写入快照会覆盖占位值，
     * 让后续 {@code saveIfAbsent} 的语义变得不可预期。</p>
     */
    @Test
    @SuppressWarnings("unchecked")
    void recordStoreShouldUseSeparateKeyFromRepository() {
        RBucket<String> bucket = mock(RBucket.class);
        when(client.<String>getBucket(eq("order-service:idem:k1:response"), any(Codec.class))).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);
        RedissonIdempotentRecordStore store =
                new RedissonIdempotentRecordStore(client, RedisKeyPrefix.of("order-service"));

        assertThat(store.find("idem:k1")).isEmpty();
        verify(client).<String>getBucket(eq("order-service:idem:k1:response"), any(Codec.class));
    }

    /**
     * 快照以 {@link IdempotentRecordCodec} 的文本格式写入，并带上 TTL。
     */
    @Test
    @SuppressWarnings("unchecked")
    void recordStoreShouldSaveEncodedRecordWithTtl() {
        RBucket<String> bucket = mock(RBucket.class);
        when(client.<String>getBucket(eq("idem:k1:response"), any(Codec.class))).thenReturn(bucket);
        IdempotentRecord record = new IdempotentRecord(
                201, "application/json", "{}".getBytes(StandardCharsets.UTF_8), Map.of("Location", "/o/1"), 1L);

        new RedissonIdempotentRecordStore(client).save("idem:k1", record, Duration.ofMinutes(5));

        verify(bucket).set(IdempotentRecordCodec.encode(record), Duration.ofMinutes(5));
    }

    /**
     * 存量值格式无法识别时返回空，让调用方按"没有快照"重新执行，而不是让接口失败。
     */
    @Test
    @SuppressWarnings("unchecked")
    void recordStoreShouldReturnEmptyForCorruptedValue() {
        RBucket<String> bucket = mock(RBucket.class);
        when(client.<String>getBucket(eq("idem:k1:response"), any(Codec.class))).thenReturn(bucket);
        when(bucket.get()).thenReturn("not-a-chaos-record");

        assertThat(new RedissonIdempotentRecordStore(client).find("idem:k1")).isEmpty();
    }

    /**
     * 快照 TTL 非法时应拒绝。
     */
    @Test
    void recordStoreShouldRejectInvalidTtl() {
        IdempotentRecord record = new IdempotentRecord(200, "text/plain", new byte[0], Map.of(), 1L);
        RedissonIdempotentRecordStore store = new RedissonIdempotentRecordStore(client);

        assertThatThrownBy(() -> store.save("k", record, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 未指定租约时应启用 watchdog（leaseTime = -1）。
     */
    @Test
    void lockShouldUseWatchdogWhenLeaseTimeIsNull() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(client.getLock("job:close")).thenReturn(lock);
        when(lock.tryLock(0L, -1L, TimeUnit.MILLISECONDS)).thenReturn(true);

        assertThat(new RedissonDistributedLock(client).tryLock("job:close", null, null)).isPresent();
        verify(lock).tryLock(0L, -1L, TimeUnit.MILLISECONDS);
    }

    /**
     * 租约到期后释放锁不应抛异常，也不应调用 unlock。
     */
    @Test
    void unlockShouldNotThrowWhenLeaseExpired() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(client.getLock("job:close")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        LockHandle handle = new RedissonDistributedLock(client)
                .tryLock("job:close", Duration.ZERO, Duration.ofSeconds(1)).orElseThrow();

        assertThatCode(handle::unlock).doesNotThrowAnyException();
        verify(lock, never()).unlock();
    }

    /**
     * unlock 与租约过期竞争抛出 IllegalMonitorStateException 时应吞掉。
     */
    @Test
    void unlockShouldSwallowIllegalMonitorState() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(client.getLock("k")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(new IllegalMonitorStateException()).when(lock).unlock();
        LockHandle handle = new RedissonDistributedLock(client).tryLock("k", null, Duration.ofSeconds(1)).orElseThrow();

        assertThatCode(handle::unlock).doesNotThrowAnyException();
    }

    /**
     * 限流器应通过单个 Lua 脚本原子执行，并使用 hash tag key。
     */
    @Test
    @SuppressWarnings("unchecked")
    void rateLimiterShouldEvaluateAtomicScript() {
        RScript script = mock(RScript.class);
        when(client.getScript(any(Codec.class))).thenReturn(script);
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), any(List.class),
                any(), any())).thenReturn(1L, 0L);
        RedissonRateLimiter limiter = new RedissonRateLimiter(client, RedisKeyPrefix.of("app"));

        assertThat(limiter.tryAcquire(new RateLimitContext("ip:1.1.1.1", 10))).isTrue();
        assertThat(limiter.tryAcquire(new RateLimitContext("ip:1.1.1.1", 10))).isFalse();
        verify(script, org.mockito.Mockito.times(2)).eval(eq(RScript.Mode.READ_WRITE), anyString(),
                eq(RScript.ReturnType.INTEGER), eq(List.of("app:chaos:rate-limit:{ip:1.1.1.1}")), eq("1000"), eq("10"));
    }

    /**
     * 未初始化的布隆过滤器 add 应给出明确提示，mightContain 返回 false。
     */
    @Test
    @SuppressWarnings("unchecked")
    void bloomFilterShouldRequireCreate() {
        RBloomFilter<Object> filter = mock(RBloomFilter.class);
        when(client.getBloomFilter("users")).thenReturn(filter);
        when(filter.isExists()).thenReturn(false);
        BloomFilterTemplate template = new BloomFilterTemplate(client);

        assertThat(template.mightContain("users", "u1")).isFalse();
        assertThatThrownBy(() -> template.add("users", "u1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("create");
    }

    /**
     * 前缀应自动补冒号，缓存 key 策略应带前缀。
     */
    @Test
    void keyPrefixShouldNormalize() {
        assertThat(RedisKeyPrefix.of("order-service").apply("k")).isEqualTo("order-service:k");
        assertThat(RedisKeyPrefix.of("order-service:").apply("k")).isEqualTo("order-service:k");
        assertThat(RedisKeyPrefix.of(" ").apply("k")).isEqualTo("k");
        assertThat(new PrefixedCacheKeyStrategy(new DefaultCacheKeyStrategy(), RedisKeyPrefix.of("app")).build("user", "1"))
                .isEqualTo("app:user:1");
    }
}
