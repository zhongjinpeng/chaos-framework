package com.michael.chaos.domain.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCacheKeyStrategyTest {

    private final DefaultCacheKeyStrategy strategy = new DefaultCacheKeyStrategy();

    /**
     * 命名空间用冒号分隔，与 Redis 社区惯例一致，运维用 SCAN 能按前缀捞出一类 key。
     */
    @Test
    void buildWithNamespaceAndKeyReturnsColonSeparated() {
        assertEquals("user:123", strategy.build("user", "123"));
    }

    /**
     * 没有命名空间时不能拼出前导冒号，否则 key 会多一层空层级。
     */
    @Test
    void buildWithNullNamespaceReturnsKeyOnly() {
        assertEquals("123", strategy.build(null, "123"));
    }

    /**
     * 空白命名空间按没有处理，避免配置里多打一个空格就产生另一套 key。
     */
    @Test
    void buildWithBlankNamespaceReturnsKeyOnly() {
        assertEquals("123", strategy.build("", "123"));
        assertEquals("123", strategy.build("   ", "123"));
    }

    /**
     * 业务 key 自带冒号时要原样保留，不能被策略截断或转义。
     */
    @Test
    void buildPreservesSpecialCharactersInKey() {
        assertEquals("cache:order:item:42", strategy.build("cache", "order:item:42"));
    }
}
