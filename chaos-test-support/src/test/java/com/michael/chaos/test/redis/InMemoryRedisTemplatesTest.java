package com.michael.chaos.test.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 内存 RedisTemplate 测试。
 */
class InMemoryRedisTemplatesTest {

    /**
     * 支持 set/get/delete。
     */
    @Test
    void shouldSupportValueOperationsAndDelete() {
        RedisTemplate<Object, Object> template = InMemoryRedisTemplates.create();

        template.opsForValue().set("k", "v");

        assertThat(template.opsForValue().get("k")).isEqualTo("v");
        assertThat(template.delete("k")).isTrue();
        assertThat(template.opsForValue().get("k")).isNull();
    }

    /**
     * 共享 backing map 可以模拟重启后读取同一份数据。
     */
    @Test
    void sharedBackingMapShouldSurviveNewTemplate() {
        Map<Object, Object> backing = new HashMap<>();
        InMemoryRedisTemplates.create(backing).opsForValue().set("client", "seed");

        assertThat(InMemoryRedisTemplates.create(backing).opsForValue().get("client")).isEqualTo("seed");
    }
}
