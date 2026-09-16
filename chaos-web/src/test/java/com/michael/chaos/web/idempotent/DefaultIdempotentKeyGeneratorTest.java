package com.michael.chaos.web.idempotent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * 默认幂等 key 生成策略测试。
 */
class DefaultIdempotentKeyGeneratorTest {

    private final DefaultIdempotentKeyGenerator generator = new DefaultIdempotentKeyGenerator();

    /**
     * 同一个请求 key 在不同用户、不同租户之间必须隔离。
     */
    @Test
    void shouldIsolateRequestKeyByTenantAndUser() {
        String userA = generator.generate(context("", "k-1", "tenant-a", "user-a"));
        String userB = generator.generate(context("", "k-1", "tenant-a", "user-b"));
        String tenantB = generator.generate(context("", "k-1", "tenant-b", "user-a"));

        assertNotEquals(userA, userB);
        assertNotEquals(userA, tenantB);
        assertEquals("idem:POST:/orders:t=tenant-a:u=user-a:k-1", userA);
    }

    /**
     * 注解固定 key 同样按用户隔离，不能成为全局锁。
     */
    @Test
    void fixedKeyShouldAlsoBeScoped() {
        assertNotEquals(
                generator.generate(context("create-order", "", "t", "u1")),
                generator.generate(context("create-order", "", "t", "u2"))
        );
    }

    /**
     * 缺少 key 时不再使用 traceId 兜底，返回空字符串交由适配层处理。
     */
    @Test
    void shouldReturnBlankWhenNoKeyProvided() {
        assertEquals("", generator.generate(context("", "", "t", "u")));
    }

    /**
     * 外部传入的超长或包含控制字符的 key 视为缺失。
     */
    @Test
    void shouldRejectIllegalRequestKey() {
        assertEquals("", generator.generate(context("", "a".repeat(129), "t", "u")));
        assertEquals("", generator.generate(context("", "k 1", "t", "u")));
        assertEquals("", generator.generate(context("", "k\n1", "t", "u")));
    }

    private static IdempotentKeyContext context(String fixedKey, String requestKey, String tenantId, String userId) {
        return new IdempotentKeyContext(fixedKey, requestKey, "POST", "/orders", "trace-1", tenantId, userId);
    }
}
