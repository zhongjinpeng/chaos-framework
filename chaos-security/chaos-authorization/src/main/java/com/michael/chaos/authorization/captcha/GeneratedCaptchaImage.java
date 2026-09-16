package com.michael.chaos.authorization.captcha;

/**
 * 服务端生成的验证码答案与图片，仅在验证码服务内部流转。
 */
public record GeneratedCaptchaImage(String answer, String imageData) {
}
