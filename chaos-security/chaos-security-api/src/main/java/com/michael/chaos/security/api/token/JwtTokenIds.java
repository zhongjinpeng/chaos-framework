package com.michael.chaos.security.api.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * JWT token 标识工具。
 *
 * <p>授权服务器（写黑名单）、资源服务器和网关（读黑名单）必须使用完全相同的标识规则，否则撤销对另一侧不可见。
 * 规则：优先使用 jti；没有 jti 时使用原始 token value 的 SHA-256 摘要，避免在 Redis 中保存 token 明文。</p>
 */
public final class JwtTokenIds {

    /**
     * token 没有过期时间时的黑名单保留时长。
     */
    public static final Duration DEFAULT_REVOCATION_TTL = Duration.ofHours(2);

    private JwtTokenIds() {
    }

    /**
     * 按 jti 优先、token 摘要兜底的规则生成 token 标识。
     *
     * @param jti JWT 的 jti claim，可为空
     * @param tokenValue 原始 token 字符串
     * @return token 标识
     */
    public static String resolve(String jti, String tokenValue) {
        if (jti != null && !jti.isBlank()) {
            return jti;
        }
        return fromTokenValue(tokenValue);
    }

    /**
     * 根据原始 token value 生成稳定 token 标识。
     */
    public static String fromTokenValue(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new IllegalArgumentException("tokenValue must not be blank");
        }
        return sha256(tokenValue);
    }

    /**
     * 按 token 过期时间计算黑名单保留时长。
     *
     * @param expiresAt token 过期时间，可为空
     * @param now 当前时间
     * @return 过期时间为空时返回 {@link #DEFAULT_REVOCATION_TTL}；已过期时返回负数或零，由实现兜底
     */
    public static Duration revocationTtl(Instant expiresAt, Instant now) {
        return expiresAt == null ? DEFAULT_REVOCATION_TTL : Duration.between(now, expiresAt);
    }

    private static String sha256(String tokenValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
