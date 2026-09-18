package com.michael.chaos.security.api.access;

import java.util.Collection;
import java.util.Objects;

/**
 * 权限编码通配匹配。
 *
 * <p>权限编码按 {@code :} 分段，通配符 {@code *} 可以出现在任意分段：</p>
 * <ul>
 *   <li>{@code *} 匹配任意权限；</li>
 *   <li>{@code order:*} 匹配 {@code order:read}、{@code order:read:self}（末段通配吃掉剩余所有分段）；</li>
 *   <li>{@code order:*:self} 匹配 {@code order:read:self}，不匹配 {@code order:read}（中间通配只吃一段）。</li>
 * </ul>
 */
public final class PermissionPatterns {

    private static final String SEPARATOR = ":";

    private static final String WILDCARD = "*";

    private PermissionPatterns() {
    }

    /**
     * 判断权限模式是否匹配请求的权限编码。
     */
    public static boolean matches(String pattern, String required) {
        String normalizedPattern = AccessCollections.normalizeString(pattern);
        String normalizedRequired = AccessCollections.normalizeString(required);
        if (normalizedPattern.isBlank() || normalizedRequired.isBlank()) {
            return false;
        }
        if (normalizedPattern.equals(normalizedRequired)) {
            return true;
        }
        if (normalizedPattern.equals(WILDCARD)) {
            return true;
        }
        if (normalizedPattern.indexOf('*') < 0) {
            return false;
        }
        return matchesSegments(
                normalizedPattern.split(SEPARATOR, -1),
                normalizedRequired.split(SEPARATOR, -1));
    }

    /**
     * 判断权限模式集合中是否有任意一条匹配请求的权限编码。
     */
    public static boolean matchesAny(Collection<String> patterns, String required) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (matches(pattern, required)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSegments(String[] patternSegments, String[] requiredSegments) {
        for (int index = 0; index < patternSegments.length; index++) {
            String patternSegment = patternSegments[index];
            boolean lastPatternSegment = index == patternSegments.length - 1;
            if (WILDCARD.equals(patternSegment) && lastPatternSegment) {
                return requiredSegments.length > index;
            }
            if (index >= requiredSegments.length) {
                return false;
            }
            if (!WILDCARD.equals(patternSegment) && !Objects.equals(patternSegment, requiredSegments[index])) {
                return false;
            }
        }
        return patternSegments.length == requiredSegments.length;
    }
}
