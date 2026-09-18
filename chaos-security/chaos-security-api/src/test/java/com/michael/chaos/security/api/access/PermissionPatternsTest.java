package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 权限编码通配匹配测试。
 */
class PermissionPatternsTest {

    /**
     * 完全相同的权限编码应直接匹配。
     */
    @Test
    void shouldMatchExactPermission() {
        assertThat(PermissionPatterns.matches("order:read", "order:read")).isTrue();
        assertThat(PermissionPatterns.matches("order:read", "order:write")).isFalse();
    }

    /**
     * 全局通配应匹配任意权限。
     */
    @Test
    void shouldMatchGlobalWildcard() {
        assertThat(PermissionPatterns.matches("*", "order:read")).isTrue();
        assertThat(PermissionPatterns.matches("*", "anything")).isTrue();
    }

    /**
     * 末段通配应吃掉剩余全部分段。
     */
    @Test
    void trailingWildcardShouldMatchRemainingSegments() {
        assertThat(PermissionPatterns.matches("order:*", "order:read")).isTrue();
        assertThat(PermissionPatterns.matches("order:*", "order:read:self")).isTrue();
        assertThat(PermissionPatterns.matches("order:*", "order")).isFalse();
        assertThat(PermissionPatterns.matches("order:*", "payment:read")).isFalse();
    }

    /**
     * 中间通配只吃一段。
     */
    @Test
    void middleWildcardShouldMatchSingleSegment() {
        assertThat(PermissionPatterns.matches("order:*:self", "order:read:self")).isTrue();
        assertThat(PermissionPatterns.matches("order:*:self", "order:read")).isFalse();
        assertThat(PermissionPatterns.matches("order:*:self", "order:read:other")).isFalse();
    }

    /**
     * 空值不应匹配。
     */
    @Test
    void blankValuesShouldNotMatch() {
        assertThat(PermissionPatterns.matches(null, "order:read")).isFalse();
        assertThat(PermissionPatterns.matches("order:read", null)).isFalse();
        assertThat(PermissionPatterns.matches("  ", "order:read")).isFalse();
    }

    /**
     * 集合匹配应命中任意一条模式。
     */
    @Test
    void shouldMatchAnyPattern() {
        Set<String> patterns = Set.of("payment:*", "order:read");
        assertThat(PermissionPatterns.matchesAny(patterns, "payment:refund")).isTrue();
        assertThat(PermissionPatterns.matchesAny(patterns, "order:read")).isTrue();
        assertThat(PermissionPatterns.matchesAny(patterns, "order:delete")).isFalse();
        assertThat(PermissionPatterns.matchesAny(Set.of(), "order:read")).isFalse();
    }
}
