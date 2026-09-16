package com.michael.chaos.authorization.captcha;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * 基于 JDK AWT 的 PNG 图形验证码生成器，可在 headless 服务端运行。
 */
public class AwtCaptchaImageGenerator implements CaptchaImageGenerator {

    private static final char[] SYMBOLS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private final SecureRandom random = new SecureRandom();

    private final int length;

    private final int width;

    private final int height;

    public AwtCaptchaImageGenerator(int length, int width, int height) {
        if (length < 4 || width < 96 || height < 36) {
            throw new IllegalArgumentException("captcha image dimensions or length are too small");
        }
        this.length = length;
        this.width = width;
        this.height = height;
    }

    @Override
    public GeneratedCaptchaImage generate() {
        String answer = randomAnswer();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            paint(graphics, answer);
            return new GeneratedCaptchaImage(answer, toDataUri(image));
        } finally {
            graphics.dispose();
        }
    }

    private String randomAnswer() {
        StringBuilder answer = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            answer.append(SYMBOLS[random.nextInt(SYMBOLS.length)]);
        }
        return answer.toString();
    }

    private void paint(Graphics2D graphics, String answer) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setPaint(new GradientPaint(0, 0, new Color(242, 247, 252), width, height,
                new Color(218, 231, 245)));
        graphics.fillRect(0, 0, width, height);

        for (int index = 0; index < 7; index++) {
            graphics.setColor(randomColor(95, 165, 105));
            graphics.drawLine(random.nextInt(width), random.nextInt(height),
                    random.nextInt(width), random.nextInt(height));
        }
        for (int index = 0; index < Math.max(32, width / 2); index++) {
            graphics.setColor(randomColor(90, 170, 95));
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            graphics.fillOval(x, y, 1 + random.nextInt(2), 1 + random.nextInt(2));
        }

        int fontSize = Math.max(22, (int) (height * 0.62));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
        int step = width / (length + 1);
        int baseline = (height + fontSize) / 2 - 3;
        for (int index = 0; index < answer.length(); index++) {
            int x = step * (index + 1) - fontSize / 3 + random.nextInt(5) - 2;
            int y = baseline + random.nextInt(5) - 2;
            AffineTransform original = graphics.getTransform();
            graphics.rotate(Math.toRadians(random.nextInt(31) - 15), x + fontSize / 3.0, y - fontSize / 3.0);
            graphics.setColor(randomColor(28, 92, 255));
            graphics.drawString(String.valueOf(answer.charAt(index)), x, y);
            graphics.setTransform(original);
        }
    }

    private Color randomColor(int min, int max, int alpha) {
        int bound = max - min + 1;
        return new Color(
                min + random.nextInt(bound),
                min + random.nextInt(bound),
                min + random.nextInt(bound),
                alpha
        );
    }

    private String toDataUri(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to generate captcha image", ex);
        }
    }
}
