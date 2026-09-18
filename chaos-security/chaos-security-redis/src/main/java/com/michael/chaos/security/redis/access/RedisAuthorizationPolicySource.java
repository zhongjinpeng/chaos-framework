package com.michael.chaos.security.redis.access;

import com.michael.chaos.security.api.access.AccessPolicyFactory;
import com.michael.chaos.security.api.access.AuthorizationPolicy;
import com.michael.chaos.security.api.access.AuthorizationPolicyJsonCodec;
import com.michael.chaos.security.api.access.AuthorizationPolicySource;
import com.michael.chaos.security.api.access.PolicyDefinition;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 从 Redis 读取 ABAC 策略。
 *
 * <p>策略以 JSON 数组存在一个 key 里（默认 {@code chaos:security:access:policies}），字段与
 * {@code chaos.security.access.policies} 配置完全一致，因此可以先在配置里调通再搬到 Redis。
 * 改完策略不需要重启：由 {@code CachingAuthorizationPolicySource} 按 TTL 重新拉取。</p>
 *
 * <p>key 使用明文 {@link StringRedisTemplate}，可以直接用 redis-cli 查看和修改，也便于运维脚本灰度发布策略。</p>
 */
public class RedisAuthorizationPolicySource implements AuthorizationPolicySource {

    /**
     * 默认策略 key。
     */
    public static final String DEFAULT_KEY = "chaos:security:access:policies";

    private final StringRedisTemplate redisTemplate;

    private final String key;

    private final AuthorizationPolicyJsonCodec codec;

    /**
     * 使用默认 key 创建策略来源。
     */
    public RedisAuthorizationPolicySource(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_KEY, new AuthorizationPolicyJsonCodec());
    }

    /**
     * 创建策略来源。
     *
     * @param redisTemplate 字符串序列化的 Redis 模板
     * @param key 策略 key，为空时使用 {@link #DEFAULT_KEY}
     * @param codec JSON 编解码器
     */
    public RedisAuthorizationPolicySource(
            StringRedisTemplate redisTemplate,
            String key,
            AuthorizationPolicyJsonCodec codec) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.key = key == null || key.isBlank() ? DEFAULT_KEY : key.trim();
        this.codec = codec == null ? new AuthorizationPolicyJsonCodec() : codec;
    }

    @Override
    public List<AuthorizationPolicy> policies() {
        String json = redisTemplate.opsForValue().get(key);
        List<PolicyDefinition> definitions = codec.decode(json, "Redis key " + key);
        return AccessPolicyFactory.create(definitions, "Redis key " + key + " 中的策略");
    }

    /**
     * 策略 key。
     */
    public String key() {
        return key;
    }

    /**
     * 把策略写入 Redis，供运维脚本和测试使用。
     */
    public void save(List<PolicyDefinition> definitions) {
        redisTemplate.opsForValue().set(key, codec.encode(definitions));
    }
}
