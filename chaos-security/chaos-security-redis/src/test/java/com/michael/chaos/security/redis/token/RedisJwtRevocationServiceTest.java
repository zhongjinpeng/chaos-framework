package com.michael.chaos.security.redis.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Redis JWT 撤销服务单元测试。
 */
class RedisJwtRevocationServiceTest {

    /**
     * key 必须是明文前缀 + token 标识，保证与 Redisson / redis-cli 写入的数据互通。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldWritePlainStringKeyWithTtl() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);

        new RedisJwtRevocationService(template).revoke("jti-1", Duration.ofMinutes(5));

        verify(ops).set("chaos:security:jwt:blacklist:jti-1", "1", Duration.ofMinutes(5));
    }

    /**
     * 已过期或为空的 TTL 使用 1 秒兜底，避免 Redis 拒绝非正 TTL 或 key 永不过期。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldUseMinimumTtlForExpiredToken() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        RedisJwtRevocationService service = new RedisJwtRevocationService(template);

        service.revoke("jti-2", Duration.ofSeconds(-3));
        service.revoke("jti-3", null);

        verify(ops).set("chaos:security:jwt:blacklist:jti-2", "1", Duration.ofSeconds(1));
        verify(ops).set("chaos:security:jwt:blacklist:jti-3", "1", Duration.ofSeconds(1));
    }

    /**
     * 查询时按同一 key 规则判断，空标识直接视为未撤销。
     */
    @Test
    void shouldCheckKeyExistence() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.hasKey("chaos:security:jwt:blacklist:jti-1")).thenReturn(true);
        RedisJwtRevocationService service = new RedisJwtRevocationService(template);

        assertThat(service.isRevoked("jti-1")).isTrue();
        assertThat(service.isRevoked(" ")).isFalse();
        verify(template, never()).hasKey((String) null);
    }

    /**
     * 空 token 标识不写入。
     */
    @Test
    void shouldIgnoreBlankTokenId() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);

        new RedisJwtRevocationService(template).revoke("", Duration.ofMinutes(1));

        verify(template, never()).opsForValue();
        verify(template, never()).hasKey(anyString());
    }
}
