package com.michael.chaos.authorization.grant;

import com.michael.chaos.authorization.session.AuthorizationSessionParameterNames;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 自定义 grant_type token 请求转换器。
 */
public class ChaosGrantAuthenticationConverter implements AuthenticationConverter {

    private final Map<String, ChaosGrantAuthenticationHandler> handlers;

    /**
     * 创建自定义 grant_type 请求转换器。
     */
    public ChaosGrantAuthenticationConverter(List<ChaosGrantAuthenticationHandler> handlers) {
        this.handlers = new LinkedHashMap<>();
        handlers.forEach(handler -> this.handlers.put(handler.grantType().getValue(), handler));
    }

    /**
     * 将 token 端点请求转换为 {@link ChaosGrantAuthenticationToken}。
     */
    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (grantType == null || !handlers.containsKey(grantType)) {
            return null;
        }
        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        if (clientPrincipal == null) {
            throw invalidRequest("client principal is required");
        }
        return new ChaosGrantAuthenticationToken(
                new AuthorizationGrantType(grantType),
                clientPrincipal,
                requestParameters(request)
        );
    }

    /**
     * 提取请求参数，单值参数保留为字符串，多值参数保留为列表。
     */
    private Map<String, Object> requestParameters(HttpServletRequest request) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        request.getParameterMap().forEach((key, values) -> {
            for (String value : values) {
                parameters.add(key, value);
            }
        });
        Map<String, Object> result = new LinkedHashMap<>();
        parameters.forEach((key, values) -> result.put(key, values.size() == 1 ? values.get(0) : List.copyOf(values)));
        putIfNotBlank(result, "ip", clientIp(request));
        putIfNotBlank(result, "user_agent", request.getHeader("User-Agent"));
        putIfNotBlank(result, AuthorizationSessionParameterNames.DEVICE_ID, request.getHeader("X-Device-Id"));
        return result;
    }

    /**
     * 写入非空上下文字段，不覆盖请求中已经显式传入的同名参数。
     */
    private void putIfNotBlank(Map<String, Object> result, String key, String value) {
        if (!result.containsKey(key) && value != null && !value.isBlank()) {
            result.put(key, value.trim());
        }
    }

    /**
     * 解析客户端 IP，优先使用代理转发请求头。
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 创建 OAuth2 invalid_request 异常。
     */
    private OAuth2AuthenticationException invalidRequest(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, description, null));
    }
}
