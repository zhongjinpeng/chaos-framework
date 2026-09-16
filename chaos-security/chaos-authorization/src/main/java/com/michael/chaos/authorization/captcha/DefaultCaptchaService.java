package com.michael.chaos.authorization.captcha;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * 默认图形验证码服务，Redis 等存储仅保存答案摘要。
 */
public class DefaultCaptchaService implements CaptchaService {

    private final CaptchaImageGenerator imageGenerator;

    private final CaptchaStore store;

    private final Duration ttl;

    public DefaultCaptchaService(CaptchaImageGenerator imageGenerator, CaptchaStore store, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("captcha ttl must be positive");
        }
        this.imageGenerator = imageGenerator;
        this.store = store;
        this.ttl = ttl;
    }

    @Override
    public CaptchaChallenge create() {
        GeneratedCaptchaImage generated = imageGenerator.generate();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        store.save(captchaId, digest(captchaId, generated.answer()), ttl);
        return new CaptchaChallenge(captchaId, generated.imageData(), ttl.toSeconds());
    }

    @Override
    public boolean verify(String captchaId, String answer) {
        if (captchaId == null || captchaId.isBlank() || answer == null || answer.isBlank()) {
            return false;
        }
        String normalizedId = captchaId.trim();
        return store.consume(normalizedId, digest(normalizedId, answer));
    }

    private String digest(String captchaId, String answer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalized = captchaId + ':' + answer.trim().toUpperCase(Locale.ROOT);
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
