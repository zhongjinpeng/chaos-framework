package com.michael.chaos.security.redis.authorization;

import com.michael.chaos.authorization.captcha.CaptchaStore;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis 图形验证码存储，使用 Lua 保证比较与删除原子执行。
 */
public class RedisCaptchaStore implements CaptchaStore {

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "if not value then return 0 end; "
                    + "redis.call('DEL', KEYS[1]); "
                    + "if value == ARGV[1] then return 1 else return -1 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    private final String keyPrefix;

    public RedisCaptchaStore(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ':';
    }

    @Override
    public void save(String captchaId, String answerDigest, Duration ttl) {
        redisTemplate.opsForValue().set(key(captchaId), answerDigest, ttl);
    }

    @Override
    public boolean consume(String captchaId, String answerDigest) {
        Long result = redisTemplate.execute(CONSUME_SCRIPT, List.of(key(captchaId)), answerDigest);
        return result != null && result == 1L;
    }

    private String key(String captchaId) {
        return keyPrefix + captchaId;
    }
}
