package com.chaos.autoconfigure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OpenAPI OAuth2 密码登录配置。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.openapi.oauth2")
public class ChaosOpenApiOAuth2Properties {

    /**
     * 是否把 OAuth2 密码登录方案写入 OpenAPI 文档；默认关闭，建议仅在开发和测试环境开启。
     */
    private boolean enabled = false;

    /**
     * 是否把每个登录方案作为全局安全要求写入 OpenAPI；多个方案之间采用 OR 关系。
     */
    private boolean globalSecurityRequirement = true;

    /**
     * 相对 token URL 的统一基地址；为空时保留相对地址，配置后可确保从业务服务端口打开 Swagger UI 仍通过网关登录。
     */
    private String tokenUrlBase = "";

    /**
     * Swagger UI 预填的客户端标识。该值会发送到浏览器，只能配置公开占位值，真实客户端凭据应由网关注入。
     */
    @NotBlank(message = "chaos.openapi.oauth2.client-id must not be blank")
    private String clientId = "gateway-injected";

    /**
     * Swagger UI 预填的客户端密钥占位值。该值会发送到浏览器，不得配置真实客户端密钥。
     */
    @NotBlank(message = "chaos.openapi.oauth2.client-secret must not be blank")
    private String clientSecret = "gateway-injected";

    /**
     * OAuth2 scope 与展示说明；配置的全部 scope 同时作为全局安全要求。
     */
    private Map<@NotBlank String, @NotBlank String> scopes = new LinkedHashMap<>();

    /**
     * 安全方案映射，key 是 OpenAPI security scheme 名称；至少配置一个登录方案。
     */
    @Valid
    @NotEmpty(message = "chaos.openapi.oauth2.schemes must not be empty")
    private Map<@NotBlank String, @Valid Scheme> schemes = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isGlobalSecurityRequirement() {
        return globalSecurityRequirement;
    }

    public void setGlobalSecurityRequirement(boolean globalSecurityRequirement) {
        this.globalSecurityRequirement = globalSecurityRequirement;
    }

    public String getTokenUrlBase() {
        return tokenUrlBase;
    }

    public void setTokenUrlBase(String tokenUrlBase) {
        this.tokenUrlBase = tokenUrlBase == null ? "" : tokenUrlBase;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public Map<String, String> getScopes() {
        return scopes;
    }

    public void setScopes(Map<String, String> scopes) {
        this.scopes = scopes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(scopes);
    }

    public Map<String, Scheme> getSchemes() {
        return schemes;
    }

    public void setSchemes(Map<String, Scheme> schemes) {
        this.schemes = schemes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(schemes);
    }

    /**
     * 单个 OpenAPI OAuth2 密码登录方案。
     */
    public static class Scheme {

        /**
         * OAuth2 token 入口；可以使用同源相对路径，经网关转发并在服务端注入客户端凭据。
         */
        @NotBlank(message = "chaos.openapi.oauth2.schemes.*.token-url must not be blank")
        private String tokenUrl;

        /**
         * Swagger Authorize 弹窗中展示的登录方案说明。
         */
        private String description;

        public String getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
