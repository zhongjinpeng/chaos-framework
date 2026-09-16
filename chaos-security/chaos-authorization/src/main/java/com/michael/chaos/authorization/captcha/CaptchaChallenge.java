package com.michael.chaos.authorization.captcha;

/**
 * 可公开返回给客户端的图形验证码挑战。
 *
 * @param captchaId 验证码一次性标识
 * @param imageData PNG data URI
 * @param expiresIn 有效期秒数
 */
public record CaptchaChallenge(String captchaId, String imageData, long expiresIn) {
}
