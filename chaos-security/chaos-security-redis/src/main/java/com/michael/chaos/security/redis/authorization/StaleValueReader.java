package com.michael.chaos.security.redis.authorization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * 容忍历史数据反序列化失败的 Redis 读取。
 *
 * <p>授权相关对象（{@code OAuth2Authorization}、{@code RegisteredClient}、
 * {@code AuthorizationSession} 等）以 JDK 序列化写入 Redis，序列化格式把<b>类结构</b>当成了存储契约：
 * 框架升级改动任意一个参与序列化的类，存量数据的 serialVersionUID 就与新类对不上，
 * 读取抛 {@link SerializationException}（{@code InvalidClassException: local class incompatible}）。</p>
 *
 * <p>这类数据已经不可用，正确处置是<b>删除并当作不存在</b>，让调用方走"令牌无效"分支：
 * 用户重新登录一次即可，坏数据被读到的那一刻自愈。若放任异常上抛，一条读不动的旧记录会把请求
 * 打成 500，而且只能靠人工清 Redis 才能恢复——这正是框架升级后"全员掉线还伴随 500"的由来。</p>
 *
 * @author michael
 */
final class StaleValueReader {

    private static final Logger LOG = LoggerFactory.getLogger(StaleValueReader.class);

    private StaleValueReader() {
    }

    /**
     * 读取一个可能由旧版本类写入的值。
     *
     * <p>反序列化失败时删除该键并返回 {@code null}，调用方按"不存在"处理。</p>
     *
     * @param redisTemplate Redis 模板
     * @param key 键
     * @return 值；键不存在或存量数据与当前类不兼容时返回 {@code null}
     */
    static Object read(RedisTemplate<Object, Object> redisTemplate, Object key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (SerializationException ex) {
            LOG.warn("丢弃与当前类不兼容的 Redis 存量数据并删除键，key={}", key, ex);
            redisTemplate.delete(key);
            return null;
        }
    }
}
