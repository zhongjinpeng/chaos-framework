package com.michael.chaos.authorization.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AwtCaptchaImageGeneratorTest {

    @Test
    void shouldGenerateReadablePngDataUri() throws Exception {
        AwtCaptchaImageGenerator generator = new AwtCaptchaImageGenerator(4, 128, 44);

        GeneratedCaptchaImage generated = generator.generate();
        byte[] bytes = Base64.getDecoder().decode(generated.imageData().substring("data:image/png;base64,".length()));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));

        assertThat(generated.answer()).hasSize(4);
        assertThat(image.getWidth()).isEqualTo(128);
        assertThat(image.getHeight()).isEqualTo(44);
    }
}
