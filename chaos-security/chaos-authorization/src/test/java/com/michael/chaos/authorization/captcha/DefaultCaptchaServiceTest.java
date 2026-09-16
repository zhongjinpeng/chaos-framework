package com.michael.chaos.authorization.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultCaptchaServiceTest {

    @Test
    void shouldCreateVerifyAndConsumeCaptchaOnce() {
        InMemoryCaptchaStore store = new InMemoryCaptchaStore();
        DefaultCaptchaService service = new DefaultCaptchaService(
                () -> new GeneratedCaptchaImage("Ab3D", "data:image/png;base64,image"),
                store,
                Duration.ofMinutes(2)
        );

        CaptchaChallenge challenge = service.create();

        assertThat(challenge.captchaId()).hasSize(32);
        assertThat(challenge.imageData()).startsWith("data:image/png;base64,");
        assertThat(challenge.expiresIn()).isEqualTo(120);
        assertThat(service.verify(challenge.captchaId(), "ab3d")).isTrue();
        assertThat(service.verify(challenge.captchaId(), "AB3D")).isFalse();
    }

    @Test
    void shouldConsumeCaptchaAfterWrongAnswer() {
        InMemoryCaptchaStore store = new InMemoryCaptchaStore();
        DefaultCaptchaService service = new DefaultCaptchaService(
                () -> new GeneratedCaptchaImage("ABCD", "data:image/png;base64,image"),
                store,
                Duration.ofMinutes(1)
        );
        CaptchaChallenge challenge = service.create();

        assertThat(service.verify(challenge.captchaId(), "WRONG")).isFalse();
        assertThat(service.verify(challenge.captchaId(), "ABCD")).isFalse();
    }

    private static class InMemoryCaptchaStore implements CaptchaStore {

        private final Map<String, String> values = new HashMap<>();

        @Override
        public void save(String captchaId, String answerDigest, Duration ttl) {
            values.put(captchaId, answerDigest);
        }

        @Override
        public boolean consume(String captchaId, String answerDigest) {
            String stored = values.remove(captchaId);
            return answerDigest.equals(stored);
        }
    }
}
