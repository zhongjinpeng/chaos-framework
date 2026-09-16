package com.michael.chaos.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 默认审计属性脱敏器测试。
 */
class DefaultAuditAttributeSanitizerTest {

    private final DefaultAuditAttributeSanitizer sanitizer = new DefaultAuditAttributeSanitizer();

    /**
     * 敏感属性名（包含匹配与精确匹配）应脱敏。
     */
    @Test
    void shouldMaskSensitiveKeys() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("password", "123456");
        attributes.put("access_token", "abc");
        attributes.put("sms-code", "8888");
        attributes.put("code", "oauth-code");

        assertThat(sanitizer.sanitize(attributes)).containsOnly(
                Map.entry("password", "******"),
                Map.entry("access_token", "******"),
                Map.entry("sms-code", "******"),
                Map.entry("code", "******"));
    }

    /**
     * orderCode、errorCode 等业务字段不应被误伤。
     */
    @Test
    void shouldNotMaskBusinessCodeFields() {
        Map<String, String> result = sanitizer.sanitize(Map.of("orderCode", "SO-1", "errorCode", "E1"));

        assertThat(result).containsEntry("orderCode", "SO-1").containsEntry("errorCode", "E1");
    }

    /**
     * 属性名不敏感但值是凭据时同样脱敏。
     */
    @Test
    void shouldMaskCredentialLikeValues() {
        Map<String, String> result = sanitizer.sanitize(Map.of(
                "detail", "Bearer abc.def",
                "raw", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig"));

        assertThat(result.values()).containsOnly("******");
    }
}
