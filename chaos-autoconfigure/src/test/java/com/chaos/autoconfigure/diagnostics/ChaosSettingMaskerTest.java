package com.chaos.autoconfigure.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 启动报告脱敏测试。
 */
class ChaosSettingMaskerTest {

    /**
     * 常见凭据键整体掩码，描述 token 类型和 Redis key 前缀的普通键保留。
     */
    @Test
    void shouldMaskCredentialKeysWithoutHidingOrdinarySettings() {
        assertThat(ChaosSettingMasker.mask("DB_PASSWORD", "secret")).isEqualTo(ChaosSettingMasker.MASK);
        assertThat(ChaosSettingMasker.mask("AWS_ACCESS_KEY_ID", "access-key")).isEqualTo(ChaosSettingMasker.MASK);
        assertThat(ChaosSettingMasker.mask("signing.private-key", "private-key")).isEqualTo(ChaosSettingMasker.MASK);
        assertThat(ChaosSettingMasker.mask("token.type", "opaque")).isEqualTo("opaque");
        assertThat(ChaosSettingMasker.mask("key-prefix", "order")).isEqualTo("order");
    }

    /**
     * 非敏感键中的 JVM 参数、Bearer token 与 URL 凭据也不能泄露。
     */
    @Test
    void shouldMaskEmbeddedSecretsAndSanitizeLocations() {
        assertThat(ChaosSettingMasker.mask(
                "JAVA_TOOL_OPTIONS", "-Ddb.password=pwd -Dauth.token=abc Bearer bearer-value"))
                .isEqualTo("-Ddb.password=****** -Dauth.token=****** Bearer ******");
        assertThat(ChaosSettingMasker.mask(
                "spring.datasource.url", "jdbc:mysql://user:pass@db.example.com:3306/demo?password=query#fragment"))
                .isEqualTo("jdbc:mysql://db.example.com:3306/demo");
        assertThat(ChaosSettingMasker.mask(
                "service.uri", "https://user:pass@example.com:9443/api/config?token=query#fragment"))
                .isEqualTo("https://example.com:9443/api/config");
    }
}
