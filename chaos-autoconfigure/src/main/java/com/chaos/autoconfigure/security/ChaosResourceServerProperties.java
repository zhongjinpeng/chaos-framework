package com.chaos.autoconfigure.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Gateway 与 Servlet 资源服务器共享的 token 校验配置。
 *
 * <p>完整前缀沿用 {@code chaos.security}。Gateway 的路由、限流等专属配置仍位于
 * {@code chaos.gateway}，token 类型和 introspection 凭据不再重复定义。</p>
 */
@Validated
@ConfigurationProperties(prefix = "chaos.security")
public class ChaosResourceServerProperties {

    @Valid
    @NotNull(message = "chaos.security.token must not be null")
    private Token token = new Token();

    @Valid
    @NotNull(message = "chaos.security.opaque-token must not be null")
    private OpaqueToken opaqueToken = new OpaqueToken();

    public Token getToken() {
        return token;
    }

    public void setToken(Token token) {
        this.token = token == null ? new Token() : token;
    }

    public OpaqueToken getOpaqueToken() {
        return opaqueToken;
    }

    public void setOpaqueToken(OpaqueToken opaqueToken) {
        this.opaqueToken = opaqueToken == null ? new OpaqueToken() : opaqueToken;
    }

    /**
     * Token 校验模式。type 为空表示 Gateway 兼容读取旧的 chaos.gateway.token.type。
     */
    public static class Token {

        private TokenType type;

        public TokenType getType() {
            return type;
        }

        public void setType(TokenType type) {
            this.type = type;
        }
    }

    /**
     * Opaque token introspection 配置。
     */
    public static class OpaqueToken {

        private String introspectionUri;

        private String clientId;

        private String clientSecret;

        @NotNull(message = "chaos.security.opaque-token.cache-ttl must not be null")
        private Duration cacheTtl = Duration.ofSeconds(30);

        @Min(value = 1, message = "chaos.security.opaque-token.cache-max-size must be positive")
        private int cacheMaxSize = 10_000;

        @NotNull(message = "chaos.security.opaque-token.connect-timeout must not be null")
        private Duration connectTimeout = Duration.ofSeconds(1);

        @NotNull(message = "chaos.security.opaque-token.read-timeout must not be null")
        private Duration readTimeout = Duration.ofSeconds(3);

        public String getIntrospectionUri() {
            return introspectionUri;
        }

        public void setIntrospectionUri(String introspectionUri) {
            this.introspectionUri = introspectionUri;
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

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }

        public int getCacheMaxSize() {
            return cacheMaxSize;
        }

        public void setCacheMaxSize(int cacheMaxSize) {
            this.cacheMaxSize = cacheMaxSize;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public enum TokenType {
        JWT,
        OPAQUE
    }
}
