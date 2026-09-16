package com.michael.chaos.authorization.core;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 授权服务器配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.authorization")
public class ChaosAuthorizationProperties {

    /**
     * 是否启用授权服务器自动装配。
     */
    private boolean enabled = true;

    /**
     * OAuth2 issuer 地址。
     */
    @NotBlank(message = "chaos.authorization.issuer must not be blank")
    private String issuer = "http://localhost:9000";

    /**
     * access token 有效期。
     */
    @NotNull(message = "chaos.authorization.access-token-ttl must not be null")
    private Duration accessTokenTtl = Duration.ofHours(2);

    /**
     * refresh token 有效期。
     */
    @NotNull(message = "chaos.authorization.refresh-token-ttl must not be null")
    private Duration refreshTokenTtl = Duration.ofDays(30);

    /**
     * 是否复用 refresh token。
     */
    private boolean reuseRefreshTokens = false;

    /**
     * token 存储与格式配置。
     */
    @Valid
    @NotNull(message = "chaos.authorization.token must not be null")
    private Token token = new Token();

    /**
     * refresh token 安全配置。
     */
    @Valid
    @NotNull(message = "chaos.authorization.refresh-token must not be null")
    private RefreshToken refreshToken = new RefreshToken();

    /**
     * 登录互踢配置。
     */
    @Valid
    @NotNull(message = "chaos.authorization.kickout must not be null")
    private Kickout kickout = new Kickout();

    /**
     * grant_type 配置。
     */
    @Valid
    @NotNull(message = "chaos.authorization.grant must not be null")
    private Grant grant = new Grant();

    /**
     * JWK 密钥配置。
     */
    @Valid
    @NotNull(message = "chaos.authorization.jwk must not be null")
    private Jwk jwk = new Jwk();

    /**
     * 客户端配置。
     */
    @Valid
    @NotNull(message = "chaos.authorization.client must not be null")
    private Client client = new Client();

    /**
     * OAuth2 授权同意记录存储配置。
     */
    @Valid
    @NotNull(message = "chaos.authorization.consent must not be null")
    private Consent consent = new Consent();

    /**
     * 生产安全检查配置。
     */
    @Valid
    @NotNull(message = "chaos.authorization.production-safety must not be null")
    private ProductionSafety productionSafety = new ProductionSafety();

    /**
     * 退出登录端点配置。
     */
    @Valid
    @NotNull(message = "chaos.authorization.logout must not be null")
    private Logout logout = new Logout();

    /**
     * 图形验证码配置。
     */
    @Valid
    @NotNull(message = "chaos.authorization.captcha must not be null")
    private Captcha captcha = new Captcha();

    /**
     * 登录失败锁定配置。
     */
    @Valid
    @NotNull(message = "chaos.authorization.login-lock must not be null")
    private LoginLock loginLock = new LoginLock();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public boolean isReuseRefreshTokens() {
        return reuseRefreshTokens;
    }

    public void setReuseRefreshTokens(boolean reuseRefreshTokens) {
        this.reuseRefreshTokens = reuseRefreshTokens;
    }

    public Token getToken() {
        return token;
    }

    public void setToken(Token token) {
        this.token = token == null ? new Token() : token;
    }

    public RefreshToken getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(RefreshToken refreshToken) {
        this.refreshToken = refreshToken == null ? new RefreshToken() : refreshToken;
    }

    public Kickout getKickout() {
        return kickout;
    }

    public void setKickout(Kickout kickout) {
        this.kickout = kickout == null ? new Kickout() : kickout;
    }

    public Grant getGrant() {
        return grant;
    }

    public void setGrant(Grant grant) {
        this.grant = grant == null ? new Grant() : grant;
    }

    public Jwk getJwk() {
        return jwk;
    }

    public void setJwk(Jwk jwk) {
        this.jwk = jwk == null ? new Jwk() : jwk;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client == null ? new Client() : client;
    }

    public Consent getConsent() {
        return consent;
    }

    public void setConsent(Consent consent) {
        this.consent = consent == null ? new Consent() : consent;
    }

    public ProductionSafety getProductionSafety() {
        return productionSafety;
    }

    public void setProductionSafety(ProductionSafety productionSafety) {
        this.productionSafety = productionSafety == null ? new ProductionSafety() : productionSafety;
    }

    public Logout getLogout() {
        return logout;
    }

    public void setLogout(Logout logout) {
        this.logout = logout == null ? new Logout() : logout;
    }

    public Captcha getCaptcha() {
        return captcha;
    }

    public void setCaptcha(Captcha captcha) {
        this.captcha = captcha == null ? new Captcha() : captcha;
    }

    public LoginLock getLoginLock() {
        return loginLock;
    }

    public void setLoginLock(LoginLock loginLock) {
        this.loginLock = loginLock == null ? new LoginLock() : loginLock;
    }

    /**
     * 用户名密码登录失败锁定配置。
     *
     * <p>password grant 默认开启且验证码默认关闭，没有失败锁定时 token 端点可以被无限次尝试密码。
     * 锁定维度为"租户 + 用户名"，客户端 IP 维度的限流应由网关 {@code chaos.gateway.rate-limit} 负责。</p>
     */
    public static class LoginLock {

        /**
         * 是否启用登录失败锁定。
         */
        private boolean enabled = true;

        /**
         * 锁定前允许的连续失败次数。
         */
        @Min(value = 1, message = "chaos.authorization.login-lock.max-failures must be positive")
        private int maxFailures = 5;

        /**
         * 锁定时长，同时也是失败计数的统计窗口。
         */
        @NotNull(message = "chaos.authorization.login-lock.lock-duration must not be null")
        private Duration lockDuration = Duration.ofMinutes(15);

        /**
         * Redis 存储 key 前缀；存在 StringRedisTemplate 时使用 Redis 实现，否则退化为单实例内存实现。
         */
        @NotBlank(message = "chaos.authorization.login-lock.redis-key-prefix must not be blank")
        private String redisKeyPrefix = "chaos:authorization:login-failure";

        /**
         * 内存实现最多保留的计数条目数。
         */
        @Min(value = 1, message = "chaos.authorization.login-lock.max-local-entries must be positive")
        private int maxLocalEntries = 100_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxFailures() {
            return maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
        }

        public Duration getLockDuration() {
            return lockDuration;
        }

        public void setLockDuration(Duration lockDuration) {
            this.lockDuration = lockDuration;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }

        public int getMaxLocalEntries() {
            return maxLocalEntries;
        }

        public void setMaxLocalEntries(int maxLocalEntries) {
            this.maxLocalEntries = maxLocalEntries;
        }
    }

    /**
     * 图形验证码配置。
     */
    public static class Captcha {

        /**
         * 是否启用图形验证码端点；启用后需要 Redis 存储验证码（多实例间共享、消费后立即失效）。
         */
        private boolean enabled = false;

        /**
         * 获取图形验证码的端点路径。
         */
        @NotBlank(message = "chaos.authorization.captcha.path must not be blank")
        private String path = "/api/v1/auth/captcha";

        /**
         * 验证码有效期。
         */
        @NotNull(message = "chaos.authorization.captcha.ttl must not be null")
        private Duration ttl = Duration.ofMinutes(2);

        /**
         * 验证码字符数，最少 4 位。
         */
        @Min(value = 4, message = "chaos.authorization.captcha.length must be at least 4")
        private int length = 4;

        /**
         * 验证码图片宽度（像素），最小 96。
         */
        @Min(value = 96, message = "chaos.authorization.captcha.width must be at least 96")
        private int width = 128;

        /**
         * 验证码图片高度（像素），最小 36。
         */
        @Min(value = 36, message = "chaos.authorization.captcha.height must be at least 36")
        private int height = 44;

        /**
         * 验证码在 Redis 中的 key 前缀。
         */
        @NotBlank(message = "chaos.authorization.captcha.redis-key-prefix must not be blank")
        private String redisKeyPrefix = "chaos:authorization:captcha";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }
    }

    /**
     * 默认 grant_type 开关配置。
     */
    public static class Grant {

        /**
         * 是否启用默认用户名密码登录。
         */
        private boolean defaultPasswordEnabled = true;

        /**
         * 是否启用默认手机号验证码登录。
         */
        private boolean defaultSmsEnabled = true;

        public boolean isDefaultPasswordEnabled() {
            return defaultPasswordEnabled;
        }

        public void setDefaultPasswordEnabled(boolean defaultPasswordEnabled) {
            this.defaultPasswordEnabled = defaultPasswordEnabled;
        }

        public boolean isDefaultSmsEnabled() {
            return defaultSmsEnabled;
        }

        public void setDefaultSmsEnabled(boolean defaultSmsEnabled) {
            this.defaultSmsEnabled = defaultSmsEnabled;
        }
    }

    /**
     * 退出登录端点配置。
     */
    public static class Logout {

        /**
         * 是否启用框架统一的退出登录端点。
         */
        private boolean enabled = true;

        /**
         * 退出登录端点路径。
         */
        @NotBlank(message = "chaos.authorization.logout.path must not be blank")
        private String path = "/api/v1/auth/logout";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    /**
     * token 格式与存储配置。
     */
    public static class Token {

        /**
         * token 类型，JWT 为自包含 token，REDIS 为引用 token。
         */
        @NotNull(message = "chaos.authorization.token.type must not be null")
        private TokenType type = TokenType.JWT;

        /**
         * Redis token 模式下使用的 key 前缀。
         */
        @NotBlank(message = "chaos.authorization.token.redis-key-prefix must not be blank")
        private String redisKeyPrefix = "chaos:authorization";

        public TokenType getType() {
            return type;
        }

        public void setType(TokenType type) {
            this.type = type == null ? TokenType.JWT : type;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }
    }

    /**
     * refresh token 安全配置。
     */
    public static class RefreshToken {

        /**
         * 是否启用 refresh token 安全增强。
         */
        private boolean securityEnabled = true;

        /**
         * 检测到重放嫌疑时是否写入审计。
         */
        private boolean auditReplayEnabled = true;

        public boolean isSecurityEnabled() {
            return securityEnabled;
        }

        public void setSecurityEnabled(boolean securityEnabled) {
            this.securityEnabled = securityEnabled;
        }

        public boolean isAuditReplayEnabled() {
            return auditReplayEnabled;
        }

        public void setAuditReplayEnabled(boolean auditReplayEnabled) {
            this.auditReplayEnabled = auditReplayEnabled;
        }
    }

    /**
     * OAuth2 授权同意记录存储配置。
     */
    public static class Consent {

        /**
         * OAuth2 授权同意记录的存储方式；AUTO 按 token/client 存储方式自动选择。
         */
        @NotNull(message = "chaos.authorization.consent.store-type must not be null")
        private ConsentStoreType storeType = ConsentStoreType.AUTO;

        /**
         * 授权同意记录在 Redis 中的 key 前缀。
         */
        @NotBlank(message = "chaos.authorization.consent.redis-key-prefix must not be blank")
        private String redisKeyPrefix = "chaos:authorization:consent";

        public ConsentStoreType getStoreType() {
            return storeType;
        }

        public void setStoreType(ConsentStoreType storeType) {
            this.storeType = storeType == null ? ConsentStoreType.AUTO : storeType;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }
    }

    /**
     * 登录互踢配置。
     */
    public static class Kickout {

        /**
         * 是否启用互踢。
         */
        private boolean enabled = false;

        /**
         * 互踢范围。
         */
        @NotNull(message = "chaos.authorization.kickout.scope must not be null")
        private Scope scope = Scope.CLIENT;

        /**
         * 授权会话索引类型。
         */
        @NotNull(message = "chaos.authorization.kickout.session-registry-type must not be null")
        private SessionRegistryType sessionRegistryType = SessionRegistryType.AUTO;

        /**
         * Redis 授权会话索引 key 前缀。
         */
        @NotBlank(message = "chaos.authorization.kickout.redis-key-prefix must not be blank")
        private String redisKeyPrefix = "chaos:authorization:kickout";

        /**
         * 本地内存授权会话索引最多保留的会话数。
         */
        @Min(value = 1, message = "chaos.authorization.kickout.max-local-sessions must be positive")
        private int maxLocalSessions = 10_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Scope getScope() {
            return scope;
        }

        public void setScope(Scope scope) {
            this.scope = scope == null ? Scope.CLIENT : scope;
        }

        public SessionRegistryType getSessionRegistryType() {
            return sessionRegistryType;
        }

        public void setSessionRegistryType(SessionRegistryType sessionRegistryType) {
            this.sessionRegistryType = sessionRegistryType == null ? SessionRegistryType.AUTO : sessionRegistryType;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }

        public int getMaxLocalSessions() {
            return maxLocalSessions;
        }

        public void setMaxLocalSessions(int maxLocalSessions) {
            this.maxLocalSessions = maxLocalSessions;
        }
    }

    /**
     * JWK 密钥配置。
     */
    public static class Jwk {

        /**
         * RSA 公钥 PEM 文件路径。
         */
        private String publicKeyLocation;

        /**
         * RSA 私钥 PEM 文件路径。
         */
        private String privateKeyLocation;

        /**
         * JWK key id。
         */
        @NotBlank(message = "chaos.authorization.jwk.key-id must not be blank")
        private String keyId = UUID.randomUUID().toString();

        /**
         * 轮换前的旧公钥，只用于发布到 JWKS 端点和验签，不用于签发。
         *
         * <p>密钥轮换时把旧公钥放在这里保留到旧 token 全部过期（至少一个 access token TTL），
         * 避免切换签名密钥的瞬间所有在途 token 验签失败。</p>
         */
        @Valid
        @NotNull(message = "chaos.authorization.jwk.previous-public-keys must not be null")
        private List<PreviousKey> previousPublicKeys = new ArrayList<>();

        public List<PreviousKey> getPreviousPublicKeys() {
            return previousPublicKeys;
        }

        public void setPreviousPublicKeys(List<PreviousKey> previousPublicKeys) {
            this.previousPublicKeys = previousPublicKeys == null ? new ArrayList<>() : previousPublicKeys;
        }

        public String getPublicKeyLocation() {
            return publicKeyLocation;
        }

        public void setPublicKeyLocation(String publicKeyLocation) {
            this.publicKeyLocation = publicKeyLocation;
        }

        public String getPrivateKeyLocation() {
            return privateKeyLocation;
        }

        public void setPrivateKeyLocation(String privateKeyLocation) {
            this.privateKeyLocation = privateKeyLocation;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }
    }

    /**
     * 轮换保留的旧公钥。
     */
    public static class PreviousKey {

        /**
         * 旧密钥的 key id，必须与旧 token header 中的 kid 一致。
         */
        @NotBlank(message = "chaos.authorization.jwk.previous-public-keys.key-id must not be blank")
        private String keyId;

        /**
         * 旧 RSA 公钥 PEM 文件路径，支持 classpath: 前缀。
         */
        @NotBlank(message = "chaos.authorization.jwk.previous-public-keys.public-key-location must not be blank")
        private String publicKeyLocation;

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getPublicKeyLocation() {
            return publicKeyLocation;
        }

        public void setPublicKeyLocation(String publicKeyLocation) {
            this.publicKeyLocation = publicKeyLocation;
        }
    }

    /**
     * OAuth2 客户端配置。
     */
    public static class Client {

        /**
         * 客户端仓储模式。
         */
        @NotNull(message = "chaos.authorization.client.store-type must not be null")
        private ClientStoreType storeType = ClientStoreType.MEMORY;

        /**
         * 默认内存客户端 ID。
         */
        @NotBlank(message = "chaos.authorization.client.id must not be blank")
        private String id = "chaos-client";

        /**
         * 默认内存客户端密钥。
         */
        @NotBlank(message = "chaos.authorization.client.secret must not be blank")
        private String secret = "{noop}chaos-secret";

        /**
         * 默认内存客户端 scopes。
         */
        private String[] scopes = {"read", "write"};

        /**
         * Redis 客户端仓储的键前缀，仅 store-type=redis 时使用。
         */
        @NotBlank(message = "chaos.authorization.client.redis-key-prefix must not be blank")
        private String redisKeyPrefix = "chaos:authorization:client";

        public ClientStoreType getStoreType() {
            return storeType;
        }

        public void setStoreType(ClientStoreType storeType) {
            this.storeType = storeType == null ? ClientStoreType.MEMORY : storeType;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String[] getScopes() {
            return scopes.clone();
        }

        public void setScopes(String[] scopes) {
            this.scopes = scopes == null ? new String[0] : scopes.clone();
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }
    }

    /**
     * 生产安全检查配置。
     */
    public static class ProductionSafety {

        /**
         * 是否按生产模式执行安全检查；为空时根据 profiles 自动判断。
         */
        private Boolean productionMode;

        /**
         * 是否启用生产安全检查。
         */
        private boolean enabled = true;

        /**
         * 发现违规项时是否阻止启动。
         *
         * <p>默认 {@code true}：默认 client secret、临时 JWK、localhost issuer、Noop 撤销服务等问题上线后
         * 分别意味着公开凭据、重启全员掉线、注销不生效，必须在启动阶段暴露而不是淹没在 WARN 日志里。
         * 迁移期可以用各项 {@code allow-*} 开关逐项放宽，或临时设置为 {@code false} 只告警。</p>
         */
        private boolean failFast = true;

        public boolean isFailFast() {
            return failFast;
        }

        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }

        /**
         * 识别为生产环境的 Spring profile 名称，默认 {@code prod,production,prd}。
         *
         * <p>必须与全局 {@code chaos.production-safety.profiles} 的默认值（{@code ProductionProfiles.DEFAULTS}）保持一致：
         * 这里保留字面量是为了让 configuration processor 能生成默认值元数据，一致性由单元测试保证。</p>
         */
        private String[] profiles = {"prod", "production", "prd"};

        /**
         * 是否允许生产环境使用内存客户端仓储。
         */
        private boolean allowMemoryClientStore = false;

        /**
         * 是否允许生产环境使用内存授权和授权同意仓储。
         */
        private boolean allowMemoryAuthorizationStore = false;

        /**
         * 是否允许生产环境在启用互踢时使用本地内存会话索引。
         */
        private boolean allowMemorySessionRegistry = false;

        /**
         * 是否允许生产环境使用启动期临时生成的 JWK。
         */
        private boolean allowGeneratedJwk = false;

        /**
         * 是否允许生产环境使用 `{noop}` 客户端密钥。
         */
        private boolean allowNoopClientSecret = false;

        /**
         * 是否允许生产环境使用 localhost issuer。
         */
        private boolean allowLocalhostIssuer = false;

        /**
         * 是否允许生产环境使用空 JWT 撤销服务。
         */
        private boolean allowNoopJwtRevocationService = false;

        /**
         * 是否允许生产环境在启用互踢时使用空互踢服务。
         */
        private boolean allowNoopKickoutService = false;

        /**
         * 是否允许生产环境使用空审计发布器或不提供审计发布器。
         */
        private boolean allowNoopAuditPublisher = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String[] getProfiles() {
            return profiles.clone();
        }

        public void setProfiles(String[] profiles) {
            this.profiles = profiles == null ? new String[0] : profiles.clone();
        }

        public Boolean getProductionMode() {
            return productionMode;
        }

        public void setProductionMode(Boolean productionMode) {
            this.productionMode = productionMode;
        }

        public boolean isAllowMemoryClientStore() {
            return allowMemoryClientStore;
        }

        public void setAllowMemoryClientStore(boolean allowMemoryClientStore) {
            this.allowMemoryClientStore = allowMemoryClientStore;
        }

        public boolean isAllowMemoryAuthorizationStore() {
            return allowMemoryAuthorizationStore;
        }

        public void setAllowMemoryAuthorizationStore(boolean allowMemoryAuthorizationStore) {
            this.allowMemoryAuthorizationStore = allowMemoryAuthorizationStore;
        }

        public boolean isAllowMemorySessionRegistry() {
            return allowMemorySessionRegistry;
        }

        public void setAllowMemorySessionRegistry(boolean allowMemorySessionRegistry) {
            this.allowMemorySessionRegistry = allowMemorySessionRegistry;
        }

        public boolean isAllowGeneratedJwk() {
            return allowGeneratedJwk;
        }

        public void setAllowGeneratedJwk(boolean allowGeneratedJwk) {
            this.allowGeneratedJwk = allowGeneratedJwk;
        }

        public boolean isAllowNoopClientSecret() {
            return allowNoopClientSecret;
        }

        public void setAllowNoopClientSecret(boolean allowNoopClientSecret) {
            this.allowNoopClientSecret = allowNoopClientSecret;
        }

        public boolean isAllowLocalhostIssuer() {
            return allowLocalhostIssuer;
        }

        public void setAllowLocalhostIssuer(boolean allowLocalhostIssuer) {
            this.allowLocalhostIssuer = allowLocalhostIssuer;
        }

        public boolean isAllowNoopJwtRevocationService() {
            return allowNoopJwtRevocationService;
        }

        public void setAllowNoopJwtRevocationService(boolean allowNoopJwtRevocationService) {
            this.allowNoopJwtRevocationService = allowNoopJwtRevocationService;
        }

        public boolean isAllowNoopKickoutService() {
            return allowNoopKickoutService;
        }

        public void setAllowNoopKickoutService(boolean allowNoopKickoutService) {
            this.allowNoopKickoutService = allowNoopKickoutService;
        }

        public boolean isAllowNoopAuditPublisher() {
            return allowNoopAuditPublisher;
        }

        public void setAllowNoopAuditPublisher(boolean allowNoopAuditPublisher) {
            this.allowNoopAuditPublisher = allowNoopAuditPublisher;
        }
    }

    /**
     * token 模式。
     */
    public enum TokenType {
        /**
         * 自包含 JWT token。
         */
        JWT,
        /**
         * 基于 Redis 存储的引用 token。
         */
        REDIS
    }

    /**
     * OAuth2 客户端仓储模式。
     */
    public enum ClientStoreType {
        /**
         * 内存客户端，仅适合本地开发。
         */
        MEMORY,
        /**
         * Spring Authorization Server JDBC 客户端仓储。
         */
        JDBC,
        /**
         * Redis 客户端仓储。
         *
         * <p>与 token.type=redis 搭配时的推荐值：授权记录持久化而客户端注册信息只在内存里，
         * 会让每次重启都作废全部已签发令牌（授权记录引用的 registeredClientId 在新进程里不存在）。</p>
         */
        REDIS
    }

    /**
     * OAuth2 授权同意记录存储模式。
     */
    public enum ConsentStoreType {
        /** 根据 token/client 配置自动选择。 */
        AUTO,
        /** 本地内存，仅适合开发。 */
        MEMORY,
        /** Spring Authorization Server JDBC 存储。 */
        JDBC,
        /** Redis 存储。 */
        REDIS
    }

    /**
     * 互踢范围。
     */
    public enum Scope {
        /**
         * 仅同一个 OAuth2 client 下互踢。
         */
        CLIENT,
        /**
         * 同一用户在同一个 OAuth2 client 和同一设备下互踢。
         */
        DEVICE,
        /**
         * 同一用户所有 client 全局互踢。
         */
        GLOBAL
    }

    /**
     * 授权会话索引类型。
     */
    public enum SessionRegistryType {
        /**
         * 自动选择，有 RedisTemplate 时使用 Redis，否则使用本地内存。
         */
        AUTO,
        /**
         * 本地内存索引，仅适合单实例。
         */
        MEMORY,
        /**
         * Redis 索引，适合生产多实例。
         */
        REDIS
    }
}
