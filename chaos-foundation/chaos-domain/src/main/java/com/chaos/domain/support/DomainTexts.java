package com.chaos.domain.support;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 领域文本值的规范化工具。
 *
 * <p>统一必填文本、可选文本和文本集合的空白处理规则。所有规范化操作都使用
 * {@link String#strip()}，确保判断与清理 Unicode 空白字符时语义一致。</p>
 */
public final class DomainTexts {

    private DomainTexts() {
    }

    /**
     * 判断文本是否包含至少一个非空白字符。
     *
     * @param value 待判断文本
     * @return 包含有效文本时返回 {@code true}
     */
    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 校验必填文本并返回去除首尾空白后的值。
     *
     * @param value 待规范化文本
     * @param exceptionSupplier 文本为空时的异常工厂，由业务模块决定错误类型和错误码
     * @return 规范化后的非空文本
     * @throws RuntimeException 文本为空或只包含空白字符时抛出业务提供的异常
     */
    public static String requireText(
            String value,
            Supplier<? extends RuntimeException> exceptionSupplier
    ) {
        String normalized = stripToNull(value);
        if (normalized == null) {
            throw Objects.requireNonNull(exceptionSupplier, "异常工厂不能为空").get();
        }
        return normalized;
    }

    /**
     * 将可选文本规范化为空值或去除首尾空白后的文本。
     *
     * @param value 待规范化文本
     * @return {@code null} 或规范化后的文本
     */
    public static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 规范化文本集合，过滤空白值并按首次出现顺序去重。
     *
     * @param values 待规范化集合，可以为 {@code null}
     * @return 不可变的规范化文本列表
     */
    public static List<String> normalizeDistinct(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(DomainTexts::stripToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
