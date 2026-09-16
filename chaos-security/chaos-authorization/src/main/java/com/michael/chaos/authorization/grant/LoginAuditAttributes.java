package com.michael.chaos.authorization.grant;

import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录审计安全属性提取器。
 *
 * <p>只提取登录日志查询所需的非敏感字段，严禁把 password、token、验证码或客户端密钥
 * 写入审计事件。</p>
 */
final class LoginAuditAttributes {

    private static final String USERNAME = "username";

    private static final String[] TENANT_KEYS = {"tenant_id", "tenantId", "tenant_code", "tenantCode"};

    private LoginAuditAttributes() {
    }

    static Map<String, String> from(
            Map<String, Object> parameters,
            AuthorizationLoginContext loginContext) {
        return from(stringValue(parameters, USERNAME), loginContext);
    }

    static Map<String, String> from(String username, AuthorizationLoginContext loginContext) {
        Map<String, String> attributes = new LinkedHashMap<>();
        putIfNotBlank(attributes, USERNAME, username);
        putIfNotBlank(attributes, "grantType", loginContext.grantType());
        putIfNotBlank(attributes, "deviceId", loginContext.deviceId());
        putIfNotBlank(attributes, "userAgent", loginContext.userAgent());
        return Map.copyOf(attributes);
    }

    static String tenant(Map<String, Object> parameters) {
        for (String key : TENANT_KEYS) {
            String value = stringValue(parameters, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String stringValue(Map<String, Object> parameters, String key) {
        if (parameters == null) {
            return "";
        }
        Object value = parameters.get(key);
        return value instanceof String text ? text.trim() : "";
    }

    private static void putIfNotBlank(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value.trim());
        }
    }
}
