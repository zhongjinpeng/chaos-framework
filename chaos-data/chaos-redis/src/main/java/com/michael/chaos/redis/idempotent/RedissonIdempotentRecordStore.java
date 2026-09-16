package com.michael.chaos.redis.idempotent;

import com.michael.chaos.core.idempotent.IdempotentRecord;
import com.michael.chaos.core.idempotent.IdempotentRecordCodec;
import com.michael.chaos.core.idempotent.IdempotentRecordStore;
import com.michael.chaos.redis.key.RedisKeyPrefix;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/**
 * 基于 Redisson 的幂等响应快照存储。
 *
 * <p>集群部署必须使用该实现：内存实现的快照只存在于处理首次请求的那个实例上，重复请求打到其他实例时
 * 拿不到快照，回放会退化成 409。</p>
 *
 * <p>快照与占位 key 分开存储（快照 key 追加 {@value #RECORD_KEY_SUFFIX} 后缀）：两者生命周期不同，
 * 占位在请求开始时写入、失败时删除，快照只在成功后写入，混在同一个 key 上会让失败释放把快照一起删掉。</p>
 *
 * <p>固定使用 {@link StringCodec}：快照由 {@link IdempotentRecordCodec} 编码成纯文本，不走 Redisson 默认编解码器。
 * 否则业务替换全局编解码器（例如换成 JSON 或 Kryo）时，滚动发布期间新旧实例读写格式不一致，回放会直接失败。</p>
 */
public class RedissonIdempotentRecordStore implements IdempotentRecordStore {

    /**
     * 快照 key 后缀。
     */
    public static final String RECORD_KEY_SUFFIX = ":response";

    private final RedissonClient redissonClient;

    private final RedisKeyPrefix keyPrefix;

    /**
     * 创建不带 key 前缀的响应快照存储。
     */
    public RedissonIdempotentRecordStore(RedissonClient redissonClient) {
        this(redissonClient, RedisKeyPrefix.none());
    }

    /**
     * 创建带 key 前缀的响应快照存储。
     */
    public RedissonIdempotentRecordStore(RedissonClient redissonClient, RedisKeyPrefix keyPrefix) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
        this.keyPrefix = keyPrefix == null ? RedisKeyPrefix.none() : keyPrefix;
    }

    /**
     * 查找响应快照。
     *
     * <p>解码失败（旧版本写入的格式、人为改坏的值）返回空而不是抛异常，调用方会按"没有快照"走正常执行路径。</p>
     */
    @Override
    public Optional<IdempotentRecord> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(IdempotentRecordCodec.decode(bucket(key).get()));
    }

    @Override
    public void save(String key, IdempotentRecord record, Duration ttl) {
        Objects.requireNonNull(record, "record must not be null");
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("idempotent ttl must be positive");
        }
        bucket(key).set(IdempotentRecordCodec.encode(record), ttl);
    }

    @Override
    public void remove(String key) {
        if (key != null && !key.isBlank()) {
            bucket(key).delete();
        }
    }

    private RBucket<String> bucket(String key) {
        return redissonClient.getBucket(keyPrefix.apply(key) + RECORD_KEY_SUFFIX, StringCodec.INSTANCE);
    }
}
