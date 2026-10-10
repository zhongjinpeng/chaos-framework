package com.chaos.autoconfigure.diagnostics;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 启动报告配置值脱敏。
 *
 * <p>诊断报告会用于日志和 actuator 响应，不能出现任何凭据。这里做两层处理：</p>
 * <ol>
 *     <li>键名命中 secret/password/credential/private-key/access-key/api-key/token 等凭据语义时整体掩码；</li>
 *     <li>普通值中的 {@code password=...}、{@code -Dclient.secret=...} 和 Bearer token 也会被清理；</li>
 *     <li>URL 去掉 userinfo、查询参数和 fragment，避免连接串中的凭据泄露。</li>
 * </ol>
 */
public final class ChaosSettingMasker {

    static final String MASK = "******";

    private static final Pattern SENSITIVE_WORD = Pattern.compile(
            "(^|[._-])(password|passwd|pwd|secret|credentials?)([._-]|$)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SENSITIVE_KEY_PAIR = Pattern.compile(
            "(^|[._-])(private|access|api|secret)[._-]?key([._-]?id)?([._-]|$)", Pattern.CASE_INSENSITIVE);

    private static final Pattern ASSIGNMENT = Pattern.compile(
            "([A-Za-z0-9_.-]+)(\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;]+)");

    private static final Pattern URL_USER_INFO = Pattern.compile(
            "([A-Za-z][A-Za-z0-9+.-]*://)[^\\s/@]+@", Pattern.CASE_INSENSITIVE);

    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)\\bBearer\\s+[^\\s,;]+");

    private static final Pattern LOCATION_KEY = Pattern.compile("(?i).*(url|uri)$");

    private ChaosSettingMasker() {
    }

    /**
     * 按键名脱敏。
     *
     * @param key 配置键或展示键
     * @param value 原始值
     * @return 可以安全展示的值
     */
    public static String mask(String key, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (isSensitiveKey(key)) {
            return MASK;
        }
        String sanitized = maskEmbeddedSecrets(value);
        if (isLocation(key, sanitized)) {
            sanitized = location(sanitized);
        }
        return escapeControlCharacters(sanitized);
    }

    /**
     * 把 URL 缩减为 {@code scheme://host[:port]}，无法解析或不含 host 时返回掩码。
     */
    public static String endpoint(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getHost() == null) {
                return MASK;
            }
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException ex) {
            return MASK;
        }
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        if (SENSITIVE_WORD.matcher(normalized).find() || SENSITIVE_KEY_PAIR.matcher(normalized).find()) {
            return true;
        }
        String[] segments = normalized.split("[._-]");
        if (segments.length == 0) {
            return false;
        }
        String last = segments[segments.length - 1];
        return "token".equals(last) || "key".equals(last);
    }

    private static String maskEmbeddedSecrets(String value) {
        Matcher matcher = ASSIGNMENT.matcher(value);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String replacement = matcher.group();
            if (isSensitiveKey(matcher.group(1))) {
                replacement = matcher.group(1) + matcher.group(2) + MASK;
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        String withoutUserInfo = URL_USER_INFO.matcher(builder).replaceAll("$1" + MASK + "@");
        return BEARER_TOKEN.matcher(withoutUserInfo).replaceAll("Bearer " + MASK);
    }

    private static boolean isLocation(String key, String value) {
        String trimmed = value.trim();
        return (key != null && LOCATION_KEY.matcher(key).matches())
                || trimmed.matches("(?i)^(jdbc:)?[a-z][a-z0-9+.-]*://.*");
    }

    private static String location(String value) {
        String trimmed = value.trim();
        boolean jdbc = trimmed.regionMatches(true, 0, "jdbc:", 0, 5);
        String candidate = jdbc ? trimmed.substring(5) : trimmed;
        try {
            URI uri = URI.create(candidate);
            URI safe = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null);
            if (safe.getHost() == null) {
                return stripQueryAndFragment(trimmed);
            }
            return (jdbc ? "jdbc:" : "") + safe;
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return stripQueryAndFragment(trimmed);
        }
    }

    private static String stripQueryAndFragment(String value) {
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        int end = value.length();
        if (query >= 0) {
            end = Math.min(end, query);
        }
        if (fragment >= 0) {
            end = Math.min(end, fragment);
        }
        return value.substring(0, end);
    }

    private static String escapeControlCharacters(String value) {
        return value.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
