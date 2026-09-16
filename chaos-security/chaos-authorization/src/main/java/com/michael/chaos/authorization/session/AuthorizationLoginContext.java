package com.michael.chaos.authorization.session;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * 授权登录上下文。
 *
 * <p>该对象保存一次 token 请求的环境信息，用于会话索引、互踢审计和生产排障。
 * 不保存密码、验证码、token 等敏感值。</p>
 *
 * @param grantType OAuth2 grant_type
 * @param deviceId 设备 ID
 * @param ip 客户端 IP
 * @param userAgent User-Agent
 */
public record AuthorizationLoginContext(
        String grantType,
        String deviceId,
        String ip,
        String userAgent
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规范化登录上下文字段。
     */
    public AuthorizationLoginContext {
        grantType = normalize(grantType);
        deviceId = normalize(deviceId);
        ip = normalize(ip);
        userAgent = normalize(userAgent);
    }

    /**
     * 从授权对象属性中读取登录上下文。
     */
    public static AuthorizationLoginContext fromAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return empty();
        }
        Object value = attributes.get(AuthorizationSessionAttributes.LOGIN_CONTEXT);
        if (value instanceof AuthorizationLoginContext context) {
            return context;
        }
        return empty();
    }

    /**
     * 返回空登录上下文。
     */
    public static AuthorizationLoginContext empty() {
        return new AuthorizationLoginContext("", "", "", "");
    }

    /**
     * 判断是否包含设备 ID。
     */
    public boolean hasDeviceId() {
        return !deviceId.isBlank();
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
