package com.michael.chaos.security.redis.authorization;

import com.michael.chaos.authorization.password.LoginFailureLimiter;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 基于 Redis 的登录失败次数限制实现，集群内所有授权服务器实例共享计数。
 *
 * <p>自增与设置过期时间通过 Lua 原子执行，避免进程在两步之间崩溃留下永不过期的计数。</p>
 */
public class RedisLoginFailureLimiter implements LoginFailureLimiter {

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[1]); "
                    + "return count",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    private final String keyPrefix;

    private final int maxFailures;

    private final Duration lockDuration;

    /**
     * 创建 Redis 登录失败限制器。
     */
    public RedisLoginFailureLimiter(
            StringRedisTemplate redisTemplate,
            String keyPrefix,
            int maxFailures,
            Duration lockDuration) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ':';
        this.maxFailures = Math.max(maxFailures, 1);
        this.lockDuration = lockDuration;
    }

    @Override
    public boolean isLocked(String key) {
        String value = redisTemplate.opsForValue().get(redisKey(key));
        if (value == null) {
            return false;
        }
        try {
            return Long.parseLong(value) >= maxFailures;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Override
    public void recordFailure(String key) {
        redisTemplate.execute(INCREMENT_SCRIPT, List.of(redisKey(key)), String.valueOf(lockDuration.toMillis()));
    }

    @Override
    public void reset(String key) {
        redisTemplate.delete(redisKey(key));
    }

    private String redisKey(String key) {
        return keyPrefix + key;
    }
}
