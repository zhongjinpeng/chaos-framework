package com.michael.chaos.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Gateway 治理配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.gateway")
public class ChaosGatewayProperties {

    /**
     * 是否启用网关鉴权。
     */
    private boolean authEnabled = true;

    /**
     * 是否启用灰度标签透传。
     */
    private boolean grayEnabled = true;

    /**
     * 鉴权白名单路径。
     */
    private List<String> whitelist = new ArrayList<>(List.of("/actuator/health"));

    /**
     * IP 黑名单。
     */
    private List<String> blacklist = new ArrayList<>();

    /**
     * 可信反向代理地址（精确 IP 或 CIDR）。
     *
     * <p>只有请求的直连地址命中该列表时才会解析 {@code X-Forwarded-For}/{@code X-Real-IP}；
     * 默认为空，表示网关直接暴露在公网，任何转发头都不可信，避免客户端伪造 IP 绕过黑名单和限流。</p>
     */
    private List<String> trustedProxies = new ArrayList<>();

    /**
     * 入站请求中需要无条件剔除的内部身份请求头。
     *
     * <p>这些请求头只能由网关在认证成功后写入，客户端自带的值一律丢弃，防止伪造用户或租户身份。</p>
     */
    private List<String> internalHeaders = new ArrayList<>(List.of("X-User-Id", "X-Tenant-Id"));

    /**
     * 是否拒绝包含路径穿越或歧义编码（{@code ..}、{@code %2e}、{@code %2f}、{@code ;}、反斜杠等）的请求。
     *
     * <p>白名单按解码后的路径匹配，而下游容器会再做一次规范化；两者不一致时可能绕过鉴权，因此默认直接返回 400。</p>
     */
    private boolean rejectAmbiguousPath = true;

    /**
     * JWT 校验配置。
     */
    @Valid
    @NotNull(message = "chaos.gateway.jwt must not be null")
    private Jwt jwt = new Jwt();

    /**
     * token 校验模式配置。
     */
    @Valid
    @NotNull(message = "chaos.gateway.token must not be null")
    private Token token = new Token();

    /**
     * opaque token introspection 配置。
     */
    @Valid
    @NotNull(message = "chaos.gateway.opaque-token must not be null")
    private OpaqueToken opaqueToken = new OpaqueToken();

    /**
     * 租户状态治理配置。
     */
    @Valid
    @NotNull(message = "chaos.gateway.tenant must not be null")
    private Tenant tenant = new Tenant();

    /**
     * 全局限流配置。
     */
    @Valid
    @NotNull(message = "chaos.gateway.rate-limit must not be null")
    private RateLimit rateLimit = new RateLimit();

    /**
     * 统一降级配置。
     */
    @Valid
    @NotNull(message = "chaos.gateway.fallback must not be null")
    private Fallback fallback = new Fallback();

    /**
     * 是否输出请求耗时日志。
     */
    private boolean requestTimingEnabled = true;

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public void setAuthEnabled(boolean authEnabled) {
        this.authEnabled = authEnabled;
    }

    public boolean isGrayEnabled() {
        return grayEnabled;
    }

    public void setGrayEnabled(boolean grayEnabled) {
        this.grayEnabled = grayEnabled;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist == null ? new ArrayList<>() : whitelist;
    }

    public List<String> getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(List<String> blacklist) {
        this.blacklist = blacklist == null ? new ArrayList<>() : blacklist;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? new ArrayList<>() : trustedProxies;
    }

    public List<String> getInternalHeaders() {
        return internalHeaders;
    }

    public void setInternalHeaders(List<String> internalHeaders) {
        this.internalHeaders = internalHeaders == null ? new ArrayList<>() : internalHeaders;
    }

    public boolean isRejectAmbiguousPath() {
        return rejectAmbiguousPath;
    }

    public void setRejectAmbiguousPath(boolean rejectAmbiguousPath) {
        this.rejectAmbiguousPath = rejectAmbiguousPath;
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

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant == null ? new Tenant() : tenant;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit == null ? new RateLimit() : rateLimit;
    }

    public Fallback getFallback() {
        return fallback;
    }

    public void setFallback(Fallback fallback) {
        this.fallback = fallback == null ? new Fallback() : fallback;
    }

    public boolean isRequestTimingEnabled() {
        return requestTimingEnabled;
    }

    public void setRequestTimingEnabled(boolean requestTimingEnabled) {
        this.requestTimingEnabled = requestTimingEnabled;
    }

    /**
     * Gateway JWT 校验配置。
     */
    public static class Jwt {

        /**
         * 是否在网关校验 JWT。
         */
        private boolean validationEnabled = true;

        /**
         * 是否在网关检查 JWT 黑名单。
         */
        private boolean revocationCheckEnabled = true;

        /**
         * JWK Set URI。
         */
        private String jwkSetUri;

        /**
         * 期望的签发方（iss）；配置后签发方不一致的 token 会被拒绝。
         */
        private String issuerUri;

        /**
         * 允许的受众（aud）列表；配置后 token 的 aud 必须至少命中其中一个。
         */
        private List<String> audiences = new ArrayList<>();

        /**
         * 允许的 JWS 签名算法，默认只接受 RS256，防止算法混淆。
         */
        private List<String> jwsAlgorithms = new ArrayList<>(List.of("RS256"));

        /**
         * exp/nbf 校验允许的时钟偏差。
         */
        @NotNull(message = "chaos.gateway.jwt.clock-skew must not be null")
        private Duration clockSkew = Duration.ofSeconds(60);

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public List<String> getAudiences() {
            return audiences;
        }

        public void setAudiences(List<String> audiences) {
            this.audiences = audiences == null ? new ArrayList<>() : audiences;
        }

        public List<String> getJwsAlgorithms() {
            return jwsAlgorithms;
        }

        public void setJwsAlgorithms(List<String> jwsAlgorithms) {
            this.jwsAlgorithms = jwsAlgorithms == null ? new ArrayList<>() : jwsAlgorithms;
        }

        public Duration getClockSkew() {
            return clockSkew;
        }

        public void setClockSkew(Duration clockSkew) {
            this.clockSkew = clockSkew;
        }

        public boolean isValidationEnabled() {
            return validationEnabled;
        }

        public void setValidationEnabled(boolean validationEnabled) {
            this.validationEnabled = validationEnabled;
        }

        public boolean isRevocationCheckEnabled() {
            return revocationCheckEnabled;
        }

        public void setRevocationCheckEnabled(boolean revocationCheckEnabled) {
            this.revocationCheckEnabled = revocationCheckEnabled;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }
    }

    /**
     * Gateway token 校验模式配置。
     */
    public static class Token {

        /**
         * token 校验类型；JWT 校验自包含 token，OPAQUE 通过授权服务器 introspection 校验引用 token。
         */
        @NotNull(message = "chaos.gateway.token.type must not be null")
        private TokenType type = TokenType.JWT;

        public TokenType getType() {
            return type;
        }

        public void setType(TokenType type) {
            this.type = type;
        }
    }

    /**
     * Gateway opaque token introspection 配置。
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
         * introspection 成功结果的本地缓存时长；实际 TTL 取该值与 token exp 的较小值，0 表示不缓存。
         *
         * <p>缓存会让已撤销 token 在 TTL 内继续可用，属于性能与撤销实时性的折中。</p>
         */
        @NotNull(message = "chaos.gateway.opaque-token.cache-ttl must not be null")
        private Duration cacheTtl = Duration.ofSeconds(30);

        /**
         * introspection 本地缓存最多保留的 token 数。
         */
        @Min(value = 1, message = "chaos.gateway.opaque-token.cache-max-size must be positive")
        private int cacheMaxSize = 10_000;

        /**
         * 调用 introspection 端点的整体超时时间，避免授权服务器变慢拖垮网关。
         */
        @NotNull(message = "chaos.gateway.opaque-token.timeout must not be null")
        private Duration timeout = Duration.ofSeconds(3);

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

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
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
     * Gateway 租户治理配置。
     */
    public static class Tenant {

        /**
         * 是否启用租户状态校验。
         */
        private boolean enabled = true;

        /**
         * 租户 ID 缺失、未知或不可访问时是否拒绝请求。
         */
        private boolean failClosed = true;

        /**
         * 租户 ID 请求头名称。
         */
        @NotBlank(message = "chaos.gateway.tenant.header-name must not be blank")
        private String headerName = "X-Tenant-Id";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailClosed() {
            return failClosed;
        }

        public void setFailClosed(boolean failClosed) {
            this.failClosed = failClosed;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }
    }

    /**
     * Gateway 全局限流配置。
     */
    public static class RateLimit {

        /**
         * 是否启用网关全局限流。
         */
        private boolean enabled = false;

        /**
         * 限流器异常时是否放行。
         */
        private boolean failOpen = true;

        /**
         * 默认每秒许可数。
         */
        @Min(value = 1, message = "chaos.gateway.rate-limit.default-permits-per-second must be positive")
        private int defaultPermitsPerSecond = 100;

        /**
         * 默认内存限流器最多保留的 key 数。
         */
        @Min(value = 1, message = "chaos.gateway.rate-limit.max-local-keys must be positive")
        private int maxLocalKeys = 10_000;

        /**
         * 跳过限流的路径（Ant 风格）。
         *
         * <p>与鉴权白名单相互独立：登录、验证码等接口通常在鉴权白名单中，但恰恰最需要限流。</p>
         */
        @NotNull(message = "chaos.gateway.rate-limit.skip-paths must not be null")
        private List<String> skipPaths = new ArrayList<>(List.of("/actuator/health"));

        public List<String> getSkipPaths() {
            return skipPaths;
        }

        public void setSkipPaths(List<String> skipPaths) {
            this.skipPaths = skipPaths == null ? new ArrayList<>() : skipPaths;
        }

        /**
         * 默认限流 key 维度。
         */
        @NotNull(message = "chaos.gateway.rate-limit.key-types must not be null")
        private List<KeyType> keyTypes = new ArrayList<>(List.of(KeyType.ROUTE, KeyType.TENANT, KeyType.USER, KeyType.IP));

        /**
         * 路径级限流规则。
         */
        @Valid
        @NotNull(message = "chaos.gateway.rate-limit.rules must not be null")
        private List<Rule> rules = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }

        public int getDefaultPermitsPerSecond() {
            return defaultPermitsPerSecond;
        }

        public void setDefaultPermitsPerSecond(int defaultPermitsPerSecond) {
            this.defaultPermitsPerSecond = defaultPermitsPerSecond;
        }

        public int getMaxLocalKeys() {
            return maxLocalKeys;
        }

        public void setMaxLocalKeys(int maxLocalKeys) {
            this.maxLocalKeys = maxLocalKeys;
        }

        public List<KeyType> getKeyTypes() {
            return keyTypes;
        }

        public void setKeyTypes(List<KeyType> keyTypes) {
            this.keyTypes = keyTypes == null ? new ArrayList<>() : keyTypes;
        }

        public List<Rule> getRules() {
            return rules;
        }

        public void setRules(List<Rule> rules) {
            this.rules = rules == null ? new ArrayList<>() : rules;
        }
    }

    /**
     * 限流 key 维度。
     */
    public enum KeyType {
        /**
         * Gateway routeId。
         */
        ROUTE,
        /**
         * 请求路径。
         */
        PATH,
        /**
         * 客户端 IP。
         */
        IP,
        /**
         * 用户 ID。
         */
        USER,
        /**
         * 租户 ID。
         */
        TENANT
    }

    /**
     * 路径级限流规则。
     */
    public static class Rule {

        /**
         * 规则 ID。
         */
        @NotBlank(message = "chaos.gateway.rate-limit.rules.id must not be blank")
        private String id;

        /**
         * Ant 风格路径表达式。
         */
        @NotBlank(message = "chaos.gateway.rate-limit.rules.path-pattern must not be blank")
        private String pathPattern;

        /**
         * 每秒许可数。
         */
        @Min(value = 1, message = "chaos.gateway.rate-limit.rules.permits-per-second must be positive")
        private int permitsPerSecond = 100;

        /**
         * 当前规则使用的 key 维度；为空时使用默认维度。
         */
        @NotNull(message = "chaos.gateway.rate-limit.rules.key-types must not be null")
        private List<KeyType> keyTypes = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public int getPermitsPerSecond() {
            return permitsPerSecond;
        }

        public void setPermitsPerSecond(int permitsPerSecond) {
            this.permitsPerSecond = permitsPerSecond;
        }

        public List<KeyType> getKeyTypes() {
            return keyTypes;
        }

        public void setKeyTypes(List<KeyType> keyTypes) {
            this.keyTypes = keyTypes == null ? new ArrayList<>() : keyTypes;
        }
    }

    /**
     * Gateway 统一降级配置。
     */
    public static class Fallback {

        /**
         * 是否启用 Gateway 统一降级异常处理。
         */
        private boolean enabled = true;

        /**
         * 是否把未知异常也转换为统一 JSON。
         */
        private boolean includeUnhandled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isIncludeUnhandled() {
            return includeUnhandled;
        }

        public void setIncludeUnhandled(boolean includeUnhandled) {
            this.includeUnhandled = includeUnhandled;
        }
    }

    /**
     * Gateway token 类型。
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
