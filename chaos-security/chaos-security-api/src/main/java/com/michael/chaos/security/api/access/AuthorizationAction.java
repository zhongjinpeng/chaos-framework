package com.michael.chaos.security.api.access;

import java.util.Objects;

/**
 * 授权动作。
 *
 * @param code 动作编码
 */
public record AuthorizationAction(String code) {

    public static final AuthorizationAction ANY = new AuthorizationAction("*");

    /**
     * 规范化动作编码。
     */
    public AuthorizationAction {
        code = Objects.requireNonNullElse(code, "").trim();
    }

    /**
     * 创建动作。
     */
    public static AuthorizationAction of(String code) {
        return new AuthorizationAction(code);
    }

    /**
     * 是否匹配请求动作。
     */
    public boolean matches(String action) {
        String requestAction = Objects.requireNonNullElse(action, "").trim();
        if (requestAction.isBlank()) {
            return false;
        }
        return Objects.equals(code, ANY.code) || Objects.equals(code, requestAction);
    }
}
