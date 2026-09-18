package com.michael.chaos.security.api.access;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * ABAC 属性值比较与正则匹配工具。
 */
final class AttributeValues {

    /**
     * 正则缓存上限，超过后整体清空，避免动态策略把内存撑爆。
     */
    private static final int PATTERN_CACHE_LIMIT = 256;

    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private AttributeValues() {
    }

    /**
     * 比较两个属性值：依次尝试数值、时间戳、日期时间、日期、时间，全部不适用时按字符串比较。
     *
     * <p>只有一侧能解析为数值或时间时视为类型不兼容（例如 {@code "abc"} 与 {@code "100"}），返回空而不是
     * 退化成字典序比较，避免出现 {@code "abc" > "100"} 这种反直觉的放行。</p>
     *
     * @return 可比较时返回比较结果，类型不兼容时返回空
     */
    static OptionalInt compare(Object left, Object right) {
        String leftText = AccessCollections.normalizeString(left);
        String rightText = AccessCollections.normalizeString(right);
        if (left == null || right == null || leftText.isBlank() || rightText.isBlank()) {
            return OptionalInt.empty();
        }
        List<Function<String, ? extends Comparable<?>>> parsers = List.of(
                BigDecimal::new,
                AttributeValues::parseInstant,
                LocalDateTime::parse,
                LocalDate::parse,
                LocalTime::parse);
        for (Function<String, ? extends Comparable<?>> parser : parsers) {
            ComparisonAttempt attempt = compareAs(leftText, rightText, parser);
            if (attempt.applicable()) {
                return attempt.result();
            }
        }
        return OptionalInt.of(leftText.compareTo(rightText));
    }

    /**
     * 判断属性值是否完整匹配正则表达式，正则非法时返回 false。
     */
    static boolean regexMatches(Object value, String regex) {
        String text = AccessCollections.normalizeString(value);
        if (value == null || regex == null || regex.isBlank()) {
            return false;
        }
        return compile(regex).map(pattern -> pattern.matcher(text).matches()).orElse(false);
    }

    @SuppressWarnings("unchecked")
    private static ComparisonAttempt compareAs(
            String left,
            String right,
            Function<String, ? extends Comparable<?>> parser) {
        Comparable<Object> parsedLeft = (Comparable<Object>) parse(left, parser);
        Comparable<Object> parsedRight = (Comparable<Object>) parse(right, parser);
        if (parsedLeft != null && parsedRight != null) {
            return new ComparisonAttempt(true, OptionalInt.of(parsedLeft.compareTo(parsedRight)));
        }
        if (parsedLeft != null || parsedRight != null) {
            return new ComparisonAttempt(true, OptionalInt.empty());
        }
        return new ComparisonAttempt(false, OptionalInt.empty());
    }

    private static Comparable<?> parse(String text, Function<String, ? extends Comparable<?>> parser) {
        try {
            return parser.apply(text);
        } catch (NumberFormatException | DateTimeParseException ex) {
            return null;
        }
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ex) {
            return OffsetDateTime.parse(text).toInstant();
        }
    }

    private static Optional<Pattern> compile(String regex) {
        Pattern cached = PATTERN_CACHE.get(regex);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            Pattern compiled = Pattern.compile(regex);
            if (PATTERN_CACHE.size() >= PATTERN_CACHE_LIMIT) {
                PATTERN_CACHE.clear();
            }
            PATTERN_CACHE.put(regex, compiled);
            return Optional.of(compiled);
        } catch (PatternSyntaxException ex) {
            return Optional.empty();
        }
    }

    /**
     * 一次按具体类型的比较尝试。
     *
     * @param applicable 两侧中至少有一侧能解析为该类型
     * @param result 两侧都能解析时的比较结果
     */
    private record ComparisonAttempt(boolean applicable, OptionalInt result) {
    }
}
