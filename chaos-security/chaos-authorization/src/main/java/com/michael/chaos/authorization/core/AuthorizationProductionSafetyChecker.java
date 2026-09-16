package com.michael.chaos.authorization.core;

import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.authorization.kickout.AuthorizationKickoutService;
import com.michael.chaos.authorization.kickout.AuthorizationSessionRegistry;
import com.michael.chaos.authorization.kickout.InMemoryAuthorizationSessionRegistry;
import com.michael.chaos.authorization.kickout.NoopAuthorizationKickoutService;
import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.api.token.NoopJwtRevocationService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * 授权服务器生产安全检查器。
 *
 * <p>该检查器在显式生产模式或命中生产 profile 时生效，用于阻止授权服务器携带本地开发默认值上线。
 * 对于非标准生产 profile，可以通过 `chaos.authorization.production-safety.profiles` 显式声明，
 * 也可以通过 `chaos.authorization.production-safety.production-mode=true` 强制启用。</p>
 */
public class AuthorizationProductionSafetyChecker implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationProductionSafetyChecker.class);

    private final ChaosAuthorizationProperties properties;

    private final Environment environment;

    private final RegisteredClientRepository registeredClientRepository;

    private final OAuth2AuthorizationService authorizationService;

    private final OAuth2AuthorizationConsentService authorizationConsentService;

    private final AuthorizationSessionRegistry sessionRegistry;

    private final AuthorizationKickoutService kickoutService;

    private final JwtRevocationService jwtRevocationService;

    private final AuditEventPublisher auditEventPublisher;

    private final boolean beanTypeChecksEnabled;

    /**
     * 创建生产安全检查器。
     *
     * @param properties 授权服务器配置
     * @param environment Spring 环境
     */
    public AuthorizationProductionSafetyChecker(ChaosAuthorizationProperties properties, Environment environment) {
        this(
                properties,
                environment,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false
        );
    }

    /**
     * 创建包含实际 Bean 类型检查的生产安全检查器。
     *
     * @param properties 授权服务器配置
     * @param environment Spring 环境
     * @param registeredClientRepository OAuth2 客户端仓储
     * @param authorizationService OAuth2 授权仓储
     * @param authorizationConsentService OAuth2 授权同意仓储
     * @param sessionRegistry 授权会话索引
     * @param kickoutService 互踢服务
     * @param jwtRevocationService JWT 撤销服务
     * @param auditEventPublisher 审计发布器
     */
    public AuthorizationProductionSafetyChecker(
            ChaosAuthorizationProperties properties,
            Environment environment,
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService authorizationService,
            OAuth2AuthorizationConsentService authorizationConsentService,
            AuthorizationSessionRegistry sessionRegistry,
            AuthorizationKickoutService kickoutService,
            JwtRevocationService jwtRevocationService,
            AuditEventPublisher auditEventPublisher) {
        this.properties = properties;
        this.environment = environment;
        this.registeredClientRepository = registeredClientRepository;
        this.authorizationService = authorizationService;
        this.authorizationConsentService = authorizationConsentService;
        this.sessionRegistry = sessionRegistry;
        this.kickoutService = kickoutService;
        this.jwtRevocationService = jwtRevocationService;
        this.auditEventPublisher = auditEventPublisher;
        this.beanTypeChecksEnabled = true;
    }

    private AuthorizationProductionSafetyChecker(
            ChaosAuthorizationProperties properties,
            Environment environment,
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService authorizationService,
            OAuth2AuthorizationConsentService authorizationConsentService,
            AuthorizationSessionRegistry sessionRegistry,
            AuthorizationKickoutService kickoutService,
            JwtRevocationService jwtRevocationService,
            AuditEventPublisher auditEventPublisher,
            boolean beanTypeChecksEnabled) {
        this.properties = properties;
        this.environment = environment;
        this.registeredClientRepository = registeredClientRepository;
        this.authorizationService = authorizationService;
        this.authorizationConsentService = authorizationConsentService;
        this.sessionRegistry = sessionRegistry;
        this.kickoutService = kickoutService;
        this.jwtRevocationService = jwtRevocationService;
        this.auditEventPublisher = auditEventPublisher;
        this.beanTypeChecksEnabled = beanTypeChecksEnabled;
    }

    /**
     * 应用启动后执行生产安全检查。
     *
     * <p>违规项（会导致凭据泄露、注销失效或重启全员掉线的配置）默认阻止启动；
     * 提示项（例如内存客户端仓储）只输出告警。</p>
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        ChaosAuthorizationProperties.ProductionSafety safety = properties.getProductionSafety();
        if (!safety.isEnabled() || !isProductionMode(safety)) {
            return;
        }
        warnings(safety).forEach(warning -> LOGGER.warn("授权服务器生产安全提示: {}", warning));
        List<String> violations = violations(safety);
        if (violations.isEmpty()) {
            return;
        }
        ChaosDiagnostic diagnostic = new ChaosDiagnostic(
                "授权服务器生产安全检查未通过，检测到 " + violations.size() + " 项只适用于本地开发的配置",
                violations,
                List.of(
                        "按上面每一项替换为生产实现：客户端与授权记录使用 redis/jdbc 存储（引入 chaos-redis-starter"
                                + " 或配置 JdbcTemplate），配置持久化 JWK（chaos.authorization.jwk.*）、非默认客户端密钥和正式 issuer",
                        "迁移期确需临时放行：逐项配置 chaos.authorization.production-safety.allow-*，"
                                + "或设置 chaos.authorization.production-safety.fail-fast=false（只告警）"));
        if (safety.isFailFast()) {
            throw new ChaosDiagnosticException(diagnostic);
        }
        LOGGER.warn(diagnostic.format());
    }

    private boolean isProductionMode(ChaosAuthorizationProperties.ProductionSafety safety) {
        Boolean productionMode = safety.getProductionMode();
        return productionMode == null ? isProductionProfile(safety) : productionMode;
    }

    private boolean isProductionProfile(ChaosAuthorizationProperties.ProductionSafety safety) {
        List<String> productionProfiles = Arrays.stream(safety.getProfiles())
                .filter(profile -> profile != null && !profile.isBlank())
                .map(String::trim)
                .toList();
        if (productionProfiles.isEmpty()) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(activeProfile -> productionProfiles.stream().anyMatch(activeProfile::equalsIgnoreCase));
    }

    private List<String> violations(ChaosAuthorizationProperties.ProductionSafety safety) {
        List<String> violations = new ArrayList<>();
        if (!safety.isAllowLocalhostIssuer() && isLocalhostIssuer(properties.getIssuer())) {
            violations.add("chaos.authorization.issuer 不能使用 localhost/127.0.0.1");
        }
        if (!safety.isAllowNoopClientSecret() && isNoopSecret(properties.getClient().getSecret())) {
            violations.add("chaos.authorization.client.secret 不能使用 {noop} 明文密钥");
        }
        if (beanTypeChecksEnabled) {
            beanViolations(safety, violations);
        }
        if (!safety.isAllowGeneratedJwk() && usesGeneratedJwk(properties.getJwk())) {
            violations.add("chaos.authorization.jwk.public-key-location/private-key-location 必须配置固定密钥");
        }
        return violations;
    }

    /**
     * 收集不应阻断启动、但需要在生产日志中显式提示的配置风险。
     */
    private List<String> warnings(ChaosAuthorizationProperties.ProductionSafety safety) {
        List<String> warnings = new ArrayList<>();
        if (!safety.isAllowMemoryClientStore()
                && properties.getClient().getStoreType() == ChaosAuthorizationProperties.ClientStoreType.MEMORY) {
            warnings.add("chaos.authorization.client.store-type 使用 memory，重启后动态注册的客户端会丢失，多实例之间也不共享；持久化客户端请改用 redis 或 jdbc");
        }
        if (beanTypeChecksEnabled
                && !safety.isAllowMemoryClientStore()
                && registeredClientRepository instanceof InMemoryRegisteredClientRepository) {
            warnings.add("RegisteredClientRepository 使用 InMemoryRegisteredClientRepository，实例间不会共享客户端注册信息");
        }
        if (beanTypeChecksEnabled
                && !safety.isAllowMemoryAuthorizationStore()
                && authorizationConsentService instanceof InMemoryOAuth2AuthorizationConsentService) {
            warnings.add("OAuth2AuthorizationConsentService 使用 InMemoryOAuth2AuthorizationConsentService，"
                    + "重启或多实例部署时授权同意记录不会保留或共享");
        }
        return warnings;
    }

    private void beanViolations(ChaosAuthorizationProperties.ProductionSafety safety, List<String> violations) {
        if (!safety.isAllowMemoryAuthorizationStore()
                && authorizationService instanceof InMemoryOAuth2AuthorizationService) {
            violations.add("OAuth2AuthorizationService 生产环境不能使用 InMemoryOAuth2AuthorizationService");
        }
        if (properties.getKickout().isEnabled()
                && !safety.isAllowMemorySessionRegistry()
                && sessionRegistry instanceof InMemoryAuthorizationSessionRegistry) {
            violations.add("chaos.authorization.kickout 启用时生产环境不能使用 InMemoryAuthorizationSessionRegistry");
        }
        if (properties.getKickout().isEnabled()
                && !safety.isAllowNoopKickoutService()
                && kickoutService instanceof NoopAuthorizationKickoutService) {
            violations.add("chaos.authorization.kickout 启用时生产环境不能使用 NoopAuthorizationKickoutService");
        }
        if (!safety.isAllowNoopJwtRevocationService()
                && jwtRevocationService instanceof NoopJwtRevocationService) {
            violations.add("JwtRevocationService 生产环境不能使用 NoopJwtRevocationService");
        }
        if (!safety.isAllowNoopAuditPublisher()
                && (auditEventPublisher == null || auditEventPublisher instanceof NoopAuditEventPublisher)) {
            violations.add("授权服务器生产环境必须提供有效 AuditEventPublisher");
        }
    }

    private boolean isLocalhostIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return true;
        }
        String normalized = issuer.toLowerCase();
        return normalized.contains("://localhost")
                || normalized.contains("://127.0.0.1")
                || normalized.contains("://0.0.0.0");
    }

    private boolean isNoopSecret(String secret) {
        return secret == null || secret.isBlank() || secret.trim().startsWith("{noop}");
    }

    private boolean usesGeneratedJwk(ChaosAuthorizationProperties.Jwk jwk) {
        return jwk.getPublicKeyLocation() == null
                || jwk.getPublicKeyLocation().isBlank()
                || jwk.getPrivateKeyLocation() == null
                || jwk.getPrivateKeyLocation().isBlank();
    }
}
