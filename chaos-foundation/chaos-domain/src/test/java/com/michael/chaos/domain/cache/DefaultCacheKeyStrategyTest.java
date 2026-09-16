package com.michael.chaos.domain.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCacheKeyStrategyTest {

    private final DefaultCacheKeyStrategy strategy = new DefaultCacheKeyStrategy();

    @Test
    void buildWithNamespaceAndKeyReturnsColonSeparated() {
        assertEquals("user:123", strategy.build("user", "123"));
    }

    @Test
    void buildWithNullNamespaceReturnsKeyOnly() {
        assertEquals("123", strategy.build(null, "123"));
    }

    @Test
    void buildWithBlankNamespaceReturnsKeyOnly() {
        assertEquals("123", strategy.build("", "123"));
        assertEquals("123", strategy.build("   ", "123"));
    }

    @Test
    void buildPreservesSpecialCharactersInKey() {
        assertEquals("cache:order:item:42", strategy.build("cache", "order:item:42"));
    }
}
