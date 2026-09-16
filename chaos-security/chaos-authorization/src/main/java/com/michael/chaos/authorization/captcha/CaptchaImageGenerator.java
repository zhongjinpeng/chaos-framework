package com.michael.chaos.authorization.captcha;

/**
 * 图形验证码图片生成 SPI。
 */
@FunctionalInterface
public interface CaptchaImageGenerator {

    /**
     * 生成新的验证码答案及其图片。
     */
    GeneratedCaptchaImage generate();
}
