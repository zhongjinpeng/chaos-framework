package com.michael.chaos.authorization.captcha;

import java.time.Duration;

/**
 * 一次性验证码存储 SPI。
 */
public interface CaptchaStore {

    /**
     * 保存验证码答案摘要。
     */
    void save(String captchaId, String answerDigest, Duration ttl);

    /**
     * 原子读取并删除验证码；无论答案是否匹配，挑战均被消费。
     */
    boolean consume(String captchaId, String answerDigest);
}
