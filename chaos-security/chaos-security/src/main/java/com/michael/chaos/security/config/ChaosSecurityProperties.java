package com.michael.chaos.security.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 安全模块配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.security")
public class ChaosSecurityProperties {

    /**
     * 是否启用安全自动装配。
     */
    private boolean enabled = true;

    /**
     * 允许匿名访问的路径列表。
     */
    private String[] permitAll = {"/actuator/health"};

    /**
     * JWT 配置。
     */
    @Valid
    @NotNull(message = "chaos.security.jwt must not be null")
    private Jwt jwt = new Jwt();

    /**
     * 资源服务器 token 校验模式配置。
     */
    @Valid
    @NotNull(message = "chaos.security.token must not be null")
    private Token token = new Token();

    /**
     * opaque token introspection 配置。
     */
    @Valid
    @NotNull(message = "chaos.security.opaque-token must not be null")
    private OpaqueToken opaqueToken = new OpaqueToken();

    /**
     * 通用授权配置。
     */
    @Valid
    @NotNull(message = "chaos.security.access must not be null")
    private Access access = new Access();

    /**
     * 是否启用 HTTP Basic。
     */
    private boolean httpBasicEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回允许匿名访问路径的防御性副本。
     */
    public String[] getPermitAll() {
        return permitAll.clone();
    }

    /**
     * 设置允许匿名访问路径。
     */
    public void setPermitAll(String[] permitAll) {
        this.permitAll = permitAll == null ? new String[0] : permitAll.clone();
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt == null ? new Jwt() : jwt;
    }

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

    public Access getAccess() {
        return access;
    }

    public void setAccess(Access access) {
        this.access = access == null ? new Access() : access;
    }

    public boolean isHttpBasicEnabled() {
        return httpBasicEnabled;
    }

    public void setHttpBasicEnabled(boolean httpBasicEnabled) {
        this.httpBasicEnabled = httpBasicEnabled;
    }

    /**
     * RBAC/ABAC 通用授权配置。
     */
    public static class Access {

        /**
         * 具备这些角色时 RBAC 默认放行。
         */
        private List<String> adminRoles = List.of("admin");

        public List<String> getAdminRoles() {
            return List.copyOf(adminRoles);
        }

        public void setAdminRoles(List<String> adminRoles) {
            this.adminRoles = adminRoles == null ? List.of() : List.copyOf(adminRoles);
        }
    }

    /**
     * JWT 资源服务器配置。
     */
    public static class Jwt {

        /**
         * 是否启用 JWT 撤销检查。
         */
        private boolean revocationCheckEnabled = true;

        /**
         * 撤销服务（如 Redis）异常时是否放行。
         *
         * <p>默认 {@code false}（fail-closed，返回 503）：已注销或被踢下线的 token 不会因为黑名单存储故障而重新生效。
         * 对可用性要求高于撤销实时性的系统可以显式改为 {@code true}，此时异常只记录告警日志。</p>
         */
        private boolean revocationFailOpen = false;

        public boolean isRevocationFailOpen() {
            return revocationFailOpen;
        }

        public void setRevocationFailOpen(boolean revocationFailOpen) {
            this.revocationFailOpen = revocationFailOpen;
        }

        public boolean isRevocationCheckEnabled() {
            return revocationCheckEnabled;
        }

        public void setRevocationCheckEnabled(boolean revocationCheckEnabled) {
            this.revocationCheckEnabled = revocationCheckEnabled;
        }
    }

    /**
     * token 校验模式配置。
     */
    public static class Token {

        /**
         * token 校验类型；JWT 校验自包含 token，OPAQUE 通过授权服务器 introspection 校验引用 token。
         */
        @NotNull(message = "chaos.security.token.type must not be null")
        private TokenType type = TokenType.JWT;

        public TokenType getType() {
            return type;
        }

        public void setType(TokenType type) {
            this.type = type;
        }
    }

    /**
     * opaque token introspection 配置。
     */
    public static class OpaqueToken {

        /**
         * introspection 端点地址。
         */
        private String introspectionUri;

        /**
         * 调用 introspection 端点使用的 OAuth2 client_id。
         */
        private String clientId;

        /**
         * 调用 introspection 端点使用的 OAuth2 client_secret。
         */
        private String clientSecret;

        /**
         * introspection 成功结果的本地缓存时长，默认 30 秒；设为 0 关闭缓存。
         *
         * <p>缓存能避免每个请求都同步调用授权服务器，代价是 token 撤销后最多在该时长内仍被接受。</p>
         */
        private Duration cacheTtl = Duration.ofSeconds(30);

        /**
         * introspection 本地缓存最大条目数，默认 10000。
         */
        private int cacheMaxSize = 10_000;

        /**
         * 连接授权服务器 introspection 端点的超时时间，默认 1 秒。
         */
        private Duration connectTimeout = Duration.ofSeconds(1);

        /**
         * 读取 introspection 响应的超时时间，默认 3 秒；避免授权服务器变慢时长期占用业务线程。
         */
        private Duration readTimeout = Duration.ofSeconds(3);

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
    }

    /**
     * 资源服务器 token 类型。
     */
    public enum TokenType {
        /**
         * JWT 自包含 token。
         */
        JWT,
        /**
         * OAuth2 opaque/reference token。
         */
        OPAQUE
    }
}
