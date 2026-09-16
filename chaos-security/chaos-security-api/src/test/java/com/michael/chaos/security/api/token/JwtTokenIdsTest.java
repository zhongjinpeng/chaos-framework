package com.michael.chaos.security.api.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * JWT token 标识测试。
 */
class JwtTokenIdsTest {

    /**
     * 存在 jti 时优先使用 jti。
     */
    @Test
    void resolveShouldPreferJti() {
        assertThat(JwtTokenIds.resolve("token-jti", "access-token")).isEqualTo("token-jti");
    }

    /**
     * 没有 jti 时使用 token value 的 SHA-256 摘要，与授权服务器写入黑名单的规则一致。
     */
    @Test
    void resolveShouldFallbackToTokenValueDigest() throws Exception {
        assertThat(JwtTokenIds.resolve(null, "access-token")).isEqualTo(sha256("access-token"));
        assertThat(JwtTokenIds.resolve(" ", "access-token")).isEqualTo(JwtTokenIds.fromTokenValue("access-token"));
    }

    /**
     * 空 token value 不能生成撤销标识。
     */
    @Test
    void fromTokenValueShouldRejectBlankValue() {
        assertThatThrownBy(() -> JwtTokenIds.fromTokenValue(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tokenValue must not be blank");
    }

    /**
     * 没有过期时间时使用默认保留时长，否则使用剩余有效期。
     */
    @Test
    void revocationTtlShouldFollowTokenExpiry() {
        Instant now = Instant.parse("2026-09-15T00:00:00Z");

        assertThat(JwtTokenIds.revocationTtl(null, now)).isEqualTo(JwtTokenIds.DEFAULT_REVOCATION_TTL);
        assertThat(JwtTokenIds.revocationTtl(now.plusSeconds(90), now)).isEqualTo(Duration.ofSeconds(90));
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }
}
