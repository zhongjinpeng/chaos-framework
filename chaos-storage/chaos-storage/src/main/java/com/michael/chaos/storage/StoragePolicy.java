package com.michael.chaos.storage;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 上传策略。
 *
 * @param maxObjectSize 单个对象最大字节数，{@code <= 0} 表示不限制
 * @param allowedContentTypes 允许的 contentType（小写，支持 {@code image/*} 通配），为空表示不限制
 */
public record StoragePolicy(long maxObjectSize, Set<String> allowedContentTypes) {

    /**
     * 规范化策略。
     */
    public StoragePolicy {
        allowedContentTypes = allowedContentTypes == null
                ? Set.of()
                : allowedContentTypes.stream()
                        .filter(type -> type != null && !type.isBlank())
                        .map(type -> type.trim().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 不做限制的策略。
     */
    public static StoragePolicy unrestricted() {
        return new StoragePolicy(0, Set.of());
    }

    /**
     * 是否限制对象大小。
     */
    public boolean limitsSize() {
        return maxObjectSize > 0;
    }

    /**
     * 判断 contentType 是否允许。忽略参数部分，例如 {@code text/plain; charset=utf-8}。
     */
    public boolean allowsContentType(String contentType) {
        if (allowedContentTypes.isEmpty()) {
            return true;
        }
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String type = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (allowedContentTypes.contains(type)) {
            return true;
        }
        int slash = type.indexOf('/');
        return slash > 0 && allowedContentTypes.contains(type.substring(0, slash) + "/*");
    }
}
