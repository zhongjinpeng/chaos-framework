package com.michael.chaos.core.idempotent.support;

import com.michael.chaos.core.idempotent.IdempotentRecord;
import com.michael.chaos.core.idempotent.IdempotentRecordStore;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于本地内存的幂等响应快照存储。
 *
 * <p>该实现适合开发和单机场景。集群部署时应替换为 Redis 等集中式实现，否则重复请求打到另一个实例上
 * 拿不到快照，会退化成 409。</p>
 *
 * <p>容量策略与 {@link InMemoryIdempotentRepository} 保持一致：先摊销清理过期条目，仍然不足时淘汰最早写入的条目，
 * 而不是拒绝写入。响应快照比幂等 key 占用大得多，因此额外限制单条响应体大小由调用方负责，
 * 这里只按条数控制上限。</p>
 */
public class InMemoryIdempotentRecordStore implements IdempotentRecordStore {

    private static final System.Logger log = System.getLogger(InMemoryIdempotentRecordStore.class.getName());

    private static final int DEFAULT_MAX_RECORDS = 1_000;

    /**
     * 单次写入最多顺带清理的过期条目数，限制最坏情况下的锁持有时间。
     */
    private static final int MAX_EXPIRED_EVICTIONS_PER_CALL = 64;

    /**
     * 按写入顺序排列的 key → 快照。只在持有对象锁时访问。
     */
    private final LinkedHashMap<String, Entry> records = new LinkedHashMap<>();

    private final int maxRecords;

    /**
     * 创建默认容量的本地内存响应快照存储。
     */
    public InMemoryIdempotentRecordStore() {
        this(DEFAULT_MAX_RECORDS);
    }

    /**
     * 创建指定最大条数的本地内存响应快照存储。
     *
     * @param maxRecords 最大保留条数
     */
    public InMemoryIdempotentRecordStore(int maxRecords) {
        this.maxRecords = Math.max(maxRecords, 1);
    }

    @Override
    public synchronized Optional<IdempotentRecord> find(String key) {
        if (key == null) {
            return Optional.empty();
        }
        Entry entry = records.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expireAt() < System.currentTimeMillis()) {
            records.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.record());
    }

    @Override
    public synchronized void save(String key, IdempotentRecord record, Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(record, "record must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        long now = System.currentTimeMillis();
        // 先移除再放入，保证覆盖写入时该 key 回到淘汰队列末尾。
        records.remove(key);
        evictExpired(now, MAX_EXPIRED_EVICTIONS_PER_CALL);
        if (records.size() >= maxRecords) {
            evictExpired(now, Integer.MAX_VALUE);
        }
        if (records.size() >= maxRecords) {
            Iterator<String> iterator = records.keySet().iterator();
            iterator.next();
            iterator.remove();
            log.log(System.Logger.Level.WARNING,
                    "InMemoryIdempotentRecordStore capacity reached (maxRecords={0}), evicted the oldest record",
                    maxRecords);
        }
        records.put(key, new Entry(record, now + Math.max(ttl.toMillis(), 0)));
    }

    @Override
    public synchronized void remove(String key) {
        if (key != null) {
            records.remove(key);
        }
    }

    /**
     * 返回当前保留的快照数量（测试和诊断用）。
     */
    public synchronized int size() {
        return records.size();
    }

    /**
     * 从最早写入的条目开始清理过期快照。
     *
     * <p>不同 key 的 TTL 可能不同，队头未过期不代表后续都未过期；这里只做摊销清理，
     * 未被清理的过期快照会在命中时或容量不足时被移除，不影响正确性。</p>
     */
    private void evictExpired(long now, int limit) {
        Iterator<Map.Entry<String, Entry>> iterator = records.entrySet().iterator();
        int evicted = 0;
        while (iterator.hasNext() && evicted < limit) {
            Map.Entry<String, Entry> entry = iterator.next();
            if (entry.getValue().expireAt() < now) {
                iterator.remove();
                evicted++;
            } else if (limit != Integer.MAX_VALUE) {
                return;
            }
        }
    }

    private record Entry(IdempotentRecord record, long expireAt) {
    }
}
