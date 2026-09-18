package com.michael.chaos.security.redis.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Redis key 前缀规范化测试。
 */
class RedisKeyPrefixesTest {

    /**
     * 空值使用默认前缀。
     */
    @Test
    void shouldUseDefaultPrefixForBlankValue() {
        assertThat(RedisKeyPrefixes.normalize(null, "chaos:authorization")).isEqualTo("chaos:authorization");
        assertThat(RedisKeyPrefixes.normalize("  ", "chaos:authorization")).isEqualTo("chaos:authorization");
    }

    /**
     * 末尾的冒号全部去掉：配置里多写一个冒号不应该让数据落到另一套 key 上。
     */
    @Test
    void shouldStripAllTrailingColons() {
        assertThat(RedisKeyPrefixes.normalize("chaos:auth:", "d")).isEqualTo("chaos:auth");
        assertThat(RedisKeyPrefixes.normalize("chaos:auth:::", "d")).isEqualTo("chaos:auth");
        assertThat(RedisKeyPrefixes.normalize(" chaos:auth ", "d")).isEqualTo("chaos:auth");
        assertThat(RedisKeyPrefixes.normalize("chaos:auth", "d")).isEqualTo("chaos:auth");
    }
}
