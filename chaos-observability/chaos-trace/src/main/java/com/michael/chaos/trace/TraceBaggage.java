package com.michael.chaos.trace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Collections;

/**
 * W3C Baggage 请求头模型。
 *
 * <p>该模型用于业务侧构造需要跨服务透传的轻量键值上下文。敏感数据、鉴权 token、
 * 手机号、身份证号等个人信息禁止放入 baggage。</p>
 *
 * @param entries baggage 键值对
 */
public record TraceBaggage(Map<String, String> entries) {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9!#$%&'*+.^_`|~-]{1,128}$");

    /**
     * 规范化 baggage 键值对。
     */
    public TraceBaggage {
        entries = entries == null ? Map.of() : sanitize(entries);
    }

    /**
     * 解析 baggage 请求头。
     */
    public static TraceBaggage parse(String baggage) {
        if (baggage == null || baggage.isBlank()) {
            return new TraceBaggage(Map.of());
        }
        Map<String, String> entries = new LinkedHashMap<>();
        for (String entry : baggage.split(",")) {
            String[] keyValue = entry.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            String key = keyValue[0].trim();
            String value = stripProperties(keyValue[1]).trim();
            if (isValidKey(key) && isValidValue(value)) {
                entries.put(key, value);
            }
        }
        return new TraceBaggage(entries);
    }

    /**
     * 从 Map 创建 baggage。
     */
    public static TraceBaggage of(Map<String, String> entries) {
        return new TraceBaggage(entries);
    }

    /**
     * 判断是否为空。
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 生成 baggage 请求头值。
     */
    public String headerValue() {
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        entries.forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(key).append('=').append(value);
        });
        return builder.toString();
    }

    private static Map<String, String> sanitize(Map<String, String> source) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = key == null ? "" : key.trim();
            String normalizedValue = value == null ? "" : value.trim();
            if (isValidKey(normalizedKey) && isValidValue(normalizedValue)) {
                sanitized.put(normalizedKey, normalizedValue);
            }
        });
        return Collections.unmodifiableMap(sanitized);
    }

    private static String stripProperties(String value) {
        int semicolonIndex = value.indexOf(';');
        return semicolonIndex < 0 ? value : value.substring(0, semicolonIndex);
    }

    private static boolean isValidKey(String key) {
        return KEY_PATTERN.matcher(key).matches();
    }

    private static boolean isValidValue(String value) {
        return value != null
                && value.length() <= 256
                && value.chars().noneMatch(ch -> ch <= 31 || ch == 127 || ch == ',' || ch == ';');
    }
}
