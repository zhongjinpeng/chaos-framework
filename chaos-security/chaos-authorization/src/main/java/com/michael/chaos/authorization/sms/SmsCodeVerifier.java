package com.michael.chaos.authorization.sms;

/**
 * 校验 {@code grant_type=sms_code} 使用的一次性短信验证码。
 */
public interface SmsCodeVerifier {

    /**
     * @param mobile 提交到 token 端点的手机号
     * @param code 提交到 token 端点的验证码
     * @return 验证码有效且可被消费时返回 {@code true}
     */
    boolean verify(String mobile, String code);
}
