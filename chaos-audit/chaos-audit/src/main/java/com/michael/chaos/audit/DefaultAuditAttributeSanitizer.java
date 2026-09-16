package com.michael.chaos.audit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 默认审计扩展属性脱敏器。
 *
 * <p>匹配规则（任一命中即脱敏）：</p>
 * <ol>
 *     <li><b>包含匹配</b>：属性名去掉 {@code _ - .} 并转小写后包含敏感关键字，例如 {@code accessToken}、
 *     {@code client_secret}、{@code sms-code}。</li>
 *     <li><b>精确匹配</b>：属性名规范化后完全等于短关键字，例如 OAuth2 授权码参数 {@code code}、{@code pin}。
 *     原实现把 {@code code} 当作包含匹配，{@code orderCode}、{@code errorCode} 等业务字段被误伤。</li>
 *     <li><b>值特征</b>：属性值形如 {@code Bearer xxx}、{@code Basic xxx} 或 JWT，
 *     即便属性名不敏感也脱敏。原实现只看属性名，{@code detail=Bearer eyJ...} 会原样落库。</li>
 * </ol>
 */
public class DefaultAuditAttributeSanitizer implements AuditAttributeSanitizer {

    /**
     * 默认包含匹配的敏感关键字（按规范化后的属性名匹配）。
     */
    public static final List<String> DEFAULT_SENSITIVE_KEYWORDS = List.of(
            "password",
            "passwd",
            "token",
            "secret",
            "credential",
            "authorization",
            "cookie",
            "privatekey",
            "apikey",
            "smscode",
            "verifycode",
            "verificationcode",
            "captcha",
            "authcode"
    );

    /**
     * 默认精确匹配的短敏感属性名，避免包含匹配误伤业务字段。
     */
    public static final Set<String> DEFAULT_EXACT_SENSITIVE_KEYS = Set.of("code", "pin", "otp", "cvv", "pwd");

    /**
     * 默认脱敏占位值。
     */
    public static final String DEFAULT_MASK_VALUE = "******";

    private static final Pattern BEARER_OR_BASIC = Pattern.compile("^(?i)(bearer|basic)\\s+\\S+.*$");

    private static final Pattern JWT = Pattern.compile("^eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*$");

    private final List<String> sensitiveKeywords;

    private final Set<String> exactSensitiveKeys;

    private final String maskValue;

    /**
     * 使用默认敏感字段关键字创建脱敏器。
     */
    public DefaultAuditAttributeSanitizer() {
        this(DEFAULT_SENSITIVE_KEYWORDS, DEFAULT_MASK_VALUE);
    }

    /**
     * 使用自定义包含匹配关键字和占位值创建脱敏器，精确匹配关键字使用默认值。
     */
    public DefaultAuditAttributeSanitizer(Collection<String> sensitiveKeywords, String maskValue) {
        this(sensitiveKeywords, DEFAULT_EXACT_SENSITIVE_KEYS, maskValue);
    }

    /**
     * 使用完整参数创建脱敏器。
     *
     * @param sensitiveKeywords 包含匹配关键字
     * @param exactSensitiveKeys 精确匹配属性名
     * @param maskValue 脱敏占位值
     */
    public DefaultAuditAttributeSanitizer(
            Collection<String> sensitiveKeywords,
            Collection<String> exactSensitiveKeys,
            String maskValue) {
        this.sensitiveKeywords = normalizeKeywords(sensitiveKeywords);
        this.exactSensitiveKeys = Set.copyOf(normalizeKeywords(exactSensitiveKeys));
        this.maskValue = maskValue == null || maskValue.isBlank() ? DEFAULT_MASK_VALUE : maskValue;
    }

    /**
     * 对属性值进行脱敏并保持原有插入顺序，便于审计排查时阅读 JSON。
     */
    @Override
    public Map<String, String> sanitize(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            String trimmedKey = key == null ? "" : key.trim();
            if (trimmedKey.isBlank()) {
                return;
            }
            String text = Objects.toString(value, "");
            sanitized.put(trimmedKey, isSensitiveKey(trimmedKey) || isSensitiveValue(text) ? maskValue : text);
        });
        return Collections.unmodifiableMap(sanitized);
    }

    private boolean isSensitiveKey(String key) {
        String normalized = normalize(key);
        return exactSensitiveKeys.contains(normalized) || sensitiveKeywords.stream().anyMatch(normalized::contains);
    }

    private static boolean isSensitiveValue(String value) {
        String trimmed = value.trim();
        return BEARER_OR_BASIC.matcher(trimmed).matches() || JWT.matcher(trimmed).matches();
    }

    private static String normalize(String value) {
        return value.replace("_", "").replace("-", "").replace(".", "").toLowerCase(Locale.ROOT);
    }

    private static List<String> normalizeKeywords(Collection<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            normalized.add(normalize(keyword.trim()));
        }
        return List.copyOf(normalized);
    }
}
