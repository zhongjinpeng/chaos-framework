package com.michael.chaos.redis.ratelimit;

import com.michael.chaos.core.ratelimit.RateLimitContext;
import com.michael.chaos.core.ratelimit.RateLimiter;
import com.michael.chaos.redis.key.RedisKeyPrefix;
import java.util.List;
import java.util.Objects;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/**
 * 基于 Redis Lua 的集群滑动窗口限流器。
 *
 * <p>旧实现的问题：{@code incrementAndGet} 与 {@code expireIfNotSet} 分两次调用不是原子的；窗口按各实例本地时钟切分，
 * 时钟偏差会导致窗口错位；固定窗口在边界处最多放行 2 倍流量。</p>
 *
 * <p>当前实现在一个 Lua 脚本中完成“读当前/上一窗口计数 → 按上一窗口剩余比例加权估算 → 未超限才自增并设置过期”，
 * 时间取自 Redis 服务端 {@code TIME}。窗口 key 使用 hash tag，保证 Redis Cluster 下同一限流 key 落在同一 slot。</p>
 */
public class RedissonRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "chaos:rate-limit:";

    private static final long WINDOW_MILLIS = 1000L;

    static final String SLIDING_WINDOW_SCRIPT = """
            if redis.replicate_commands then pcall(redis.replicate_commands) end
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local time = redis.call('TIME')
            local nowMs = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local current = math.floor(nowMs / window)
            local currentKey = KEYS[1] .. ':' .. current
            local previousKey = KEYS[1] .. ':' .. (current - 1)
            local previousCount = tonumber(redis.call('GET', previousKey) or '0')
            local currentCount = tonumber(redis.call('GET', currentKey) or '0')
            local elapsedRatio = (nowMs % window) / window
            if previousCount * (1 - elapsedRatio) + currentCount >= limit then
                return 0
            end
            currentCount = redis.call('INCR', currentKey)
            if currentCount == 1 then
                redis.call('PEXPIRE', currentKey, window * 2)
            end
            return 1
            """;

    private final RedissonClient redissonClient;

    private final RedisKeyPrefix keyPrefix;

    /**
     * 创建不带应用前缀的 Redis 限流器。
     */
    public RedissonRateLimiter(RedissonClient redissonClient) {
        this(redissonClient, RedisKeyPrefix.none());
    }

    /**
     * 创建带应用前缀的 Redis 限流器。
     */
    public RedissonRateLimiter(RedissonClient redissonClient, RedisKeyPrefix keyPrefix) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
        this.keyPrefix = keyPrefix == null ? RedisKeyPrefix.none() : keyPrefix;
    }

    /**
     * 原子判断并占用一次许可。
     */
    @Override
    public boolean tryAcquire(RateLimitContext context) {
        if (context.permitsPerSecond() <= 0) {
            return false;
        }
        // hash tag 包住业务 key，脚本内拼出的窗口 key 与 KEYS[1] 位于同一 slot。
        String baseKey = keyPrefix.apply(KEY_PREFIX + "{" + context.key() + "}");
        Long allowed = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                SLIDING_WINDOW_SCRIPT,
                RScript.ReturnType.INTEGER,
                List.of(baseKey),
                String.valueOf(WINDOW_MILLIS),
                String.valueOf(context.permitsPerSecond()));
        return allowed != null && allowed == 1L;
    }
}
