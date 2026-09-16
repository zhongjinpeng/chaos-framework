package com.michael.chaos.authorization.captcha;

/**
 * 图形验证码签发与一次性校验服务。
 */
public interface CaptchaService {

    /**
     * 创建验证码挑战。
     */
    CaptchaChallenge create();

    /**
     * 校验并消费验证码。
     */
    boolean verify(String captchaId, String answer);
}
