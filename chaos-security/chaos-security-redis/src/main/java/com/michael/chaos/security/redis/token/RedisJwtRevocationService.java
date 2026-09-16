package com.michael.chaos.security.redis.token;

import com.michael.chaos.security.api.token.JwtRevocationService;
import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 Redis 的 JWT 黑名单撤销服务（全框架唯一实现）。
 *
 * <p>历史上存在两份实现：授权服务器使用 {@code RedisTemplate<Object, Object>}，资源服务器与网关使用 Redisson。
 * 两者 key 前缀相同，但 {@code RedisTemplate<Object, Object>} 默认使用 JDK 序列化 key，写入的实际 key 是
 * 带序列化头的二进制串，Redisson 按原始字符串查询永远查不到——授权服务器注销后资源服务器看不到撤销。
 * 这里统一使用 {@link StringRedisTemplate}：key 为 UTF-8 明文，与 Redisson、redis-cli 以及旧版 Redisson 写入的数据兼容；
 * 同时只依赖 Spring Data Redis，无论底层是 Lettuce 还是 Redisson 连接工厂都可以使用。</p>
 *
 * <p>黑名单需要在授权服务器、资源服务器和网关之间共享，因此 key 不加应用前缀。</p>
 */
public class RedisJwtRevocationService implements JwtRevocationService {

    /**
     * 黑名单 key 前缀，授权服务器、资源服务器与网关必须一致。
     */
    public static final String KEY_PREFIX = "chaos:security:jwt:blacklist:";

    private static final Duration MIN_TTL = Duration.ofSeconds(1);

    private final StringRedisTemplate redisTemplate;

    /**
     * 创建 Redis JWT 撤销服务。
     *
     * @param redisTemplate 使用字符串序列化的 Redis 模板
     */
    public RedisJwtRevocationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    /**
     * 判断 token 标识是否在黑名单中。
     */
    @Override
    public boolean isRevoked(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(tokenId)));
    }

    /**
     * 写入黑名单；TTL 为空或已过期时使用 1 秒兜底，保证 key 最终会被清理。
     */
    @Override
    public void revoke(String tokenId, Duration ttl) {
        if (tokenId == null || tokenId.isBlank()) {
            return;
        }
        Duration effectiveTtl = ttl == null || ttl.compareTo(MIN_TTL) < 0 ? MIN_TTL : ttl;
        redisTemplate.opsForValue().set(key(tokenId), "1", effectiveTtl);
    }

    /**
     * 返回 token 标识对应的 Redis key。
     */
    public static String key(String tokenId) {
        return KEY_PREFIX + tokenId;
    }
}
