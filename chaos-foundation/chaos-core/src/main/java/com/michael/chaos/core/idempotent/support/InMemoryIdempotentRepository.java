package com.michael.chaos.core.idempotent.support;

import com.michael.chaos.core.idempotent.IdempotentRepository;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 基于本地内存的幂等 key 存储。
 *
 * <p>该实现适合开发和单机场景。集群部署时应替换为 Redis 等集中式存储实现。</p>
 *
 * <p>容量策略：达到 {@code maxKeys} 时先清理过期 key，仍然不足则淘汰最早写入的 key，
 * 而不是拒绝所有新 key。原实现容量耗尽后对所有新请求返回 409，攻击者只需用随机
 * Idempotency-Key 刷满容量即可让全站写接口不可用。</p>
 *
 * <p>性能策略：过期清理按插入顺序从队头摊销执行，单次操作不再全量扫描 map。</p>
 */
public class InMemoryIdempotentRepository implements IdempotentRepository {

    private static final System.Logger log = System.getLogger(InMemoryIdempotentRepository.class.getName());
    private static final int DEFAULT_MAX_KEYS = 10_000;

    /**
     * 单次写入最多顺带清理的过期条目数，限制最坏情况下的锁持有时间。
     */
    private static final int MAX_EXPIRED_EVICTIONS_PER_CALL = 64;

    /**
     * 按写入顺序排列的 key → 过期时间（毫秒）。只在持有对象锁时访问。
     */
    private final LinkedHashMap<String, Long> keys = new LinkedHashMap<>();

    private final int maxKeys;

    /**
     * 创建默认容量的本地内存幂等仓储。
     */
    public InMemoryIdempotentRepository() {
        this(DEFAULT_MAX_KEYS);
    }

    /**
     * 创建指定最大 key 数的本地内存幂等仓储。
     *
     * @param maxKeys 最大保留 key 数
     */
    public InMemoryIdempotentRepository(int maxKeys) {
        this.maxKeys = Math.max(maxKeys, 1);
    }

    /**
     * 保存尚未存在的幂等 key。
     */
    @Override
    public synchronized boolean saveIfAbsent(String key, Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        long now = System.currentTimeMillis();
        Long existingExpireAt = keys.get(key);
        if (existingExpireAt != null) {
            if (existingExpireAt >= now) {
                return false;
            }
            keys.remove(key);
        }
        evictExpired(now, MAX_EXPIRED_EVICTIONS_PER_CALL);
        if (keys.size() >= maxKeys) {
            evictExpired(now, Integer.MAX_VALUE);
        }
        if (keys.size() >= maxKeys) {
            Iterator<String> iterator = keys.keySet().iterator();
            iterator.next();
            iterator.remove();
            log.log(System.Logger.Level.WARNING,
                    "InMemoryIdempotentRepository capacity reached (maxKeys={0}), evicted the oldest key", maxKeys);
        }
        keys.put(key, now + Math.max(ttl.toMillis(), 0));
        return true;
    }

    @Override
    public synchronized void remove(String key) {
        if (key != null) {
            keys.remove(key);
        }
    }

    /**
     * 返回当前保留的 key 数量（测试和诊断用）。
     */
    public synchronized int size() {
        return keys.size();
    }

    /**
     * 从最早写入的条目开始清理过期 key。
     *
     * <p>不同 key 的 TTL 可能不同，队头未过期不代表后续都未过期；这里只做摊销清理，
     * 未被清理的过期 key 会在命中时或容量不足时被移除，不影响正确性。</p>
     */
    private void evictExpired(long now, int limit) {
        Iterator<Map.Entry<String, Long>> iterator = keys.entrySet().iterator();
        int evicted = 0;
        while (iterator.hasNext() && evicted < limit) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() < now) {
                iterator.remove();
                evicted++;
            } else if (limit != Integer.MAX_VALUE) {
                return;
            }
        }
    }
}
