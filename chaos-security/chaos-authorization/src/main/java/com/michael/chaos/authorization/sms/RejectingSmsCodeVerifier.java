package com.michael.chaos.authorization.sms;

/**
 * 默认拒绝所有验证码的实现。
 *
 * <p>业务系统启用 sms_code 登录时必须提供自己的 {@link SmsCodeVerifier} Bean。</p>
 */
public class RejectingSmsCodeVerifier implements SmsCodeVerifier {

    /**
     * 默认始终返回校验失败。
     */
    @Override
    public boolean verify(String mobile, String code) {
        return false;
    }
}
