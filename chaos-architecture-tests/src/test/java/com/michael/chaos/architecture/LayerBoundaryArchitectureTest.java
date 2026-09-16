package com.michael.chaos.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * 架构边界测试。
 *
 * <p>这里用源码扫描约束最基础的模块边界，避免核心契约层误引 Spring、数据库、Redis、MQ 或对象存储 SDK。
 * 这些规则运行成本低，适合在本地和 CI 的 test 阶段默认执行。</p>
 */
class LayerBoundaryArchitectureTest {

    /**
     * 核心契约源码目录。
     *
     * <p>锁、幂等、缓存 key 等契约接口位于 chaos-core，Redis 实现位于 chaos-redis，
     * 因此这里不再单独列出历史上规划过、但并不存在的 chaos-cache / chaos-lock / chaos-idempotent 模块。
     * 列表中的目录必须真实存在，避免模块改名后规则被静默跳过。</p>
     */
    private static final List<String> CONTRACT_SOURCE_ROOTS = List.of(
            "chaos-foundation/chaos-core/src/main/java",
            "chaos-security/chaos-security-api/src/main/java",
            "chaos-foundation/chaos-domain/src/main/java",
            "chaos-audit/chaos-audit/src/main/java",
            "chaos-tenant/src/main/java",
            "chaos-mq/chaos-mq/src/main/java",
            "chaos-storage/chaos-storage/src/main/java"
    );

    /**
     * 契约模块中允许依赖框架的可选适配器包：只有引入对应框架的应用才会加载，契约本身仍保持框架无关。
     */
    private static final List<String> CONTRACT_ADAPTER_PACKAGES = List.of(
            "chaos-tenant/src/main/java/com/michael/chaos/tenant/servlet",
            "chaos-mq/chaos-mq/src/main/java/com/michael/chaos/mq/reliable/actuate"
    );

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "org.springframework.",
            "jakarta.persistence.",
            "jakarta.servlet.",
            "com.baomidou.",
            "org.redisson.",
            "org.springframework.data.redis.",
            "org.springframework.kafka.",
            "org.apache.rocketmq.",
            "io.minio.",
            "com.aliyun.oss."
    );

    /**
     * 核心契约模块不得依赖基础设施框架。
     */
    @Test
    void contractModulesShouldNotImportInfrastructureFrameworks() throws IOException {
        Path root = projectRoot();
        List<String> violations = new ArrayList<>();

        for (String sourceRoot : CONTRACT_SOURCE_ROOTS) {
            Path path = root.resolve(sourceRoot);
            if (!Files.exists(path)) {
                violations.add(sourceRoot + " -> 契约源码目录不存在，请同步更新 CONTRACT_SOURCE_ROOTS");
                continue;
            }
            for (Path javaFile : javaFiles(path)) {
                if (CONTRACT_ADAPTER_PACKAGES.stream().map(root::resolve).anyMatch(javaFile::startsWith)) {
                    continue;
                }
                List<String> lines = Files.readAllLines(javaFile, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (!line.startsWith("import ")) {
                        continue;
                    }
                    for (String forbiddenPrefix : FORBIDDEN_IMPORT_PREFIXES) {
                        String forbiddenImport = "import " + forbiddenPrefix;
                        if (line.startsWith(forbiddenImport)) {
                            violations.add(root.relativize(javaFile) + ":" + (i + 1) + " -> " + line);
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "核心契约模块出现基础设施依赖:\n" + String.join("\n", violations));
    }

    /**
     * starter 只做依赖聚合，不承载 Java 代码。
     */
    @Test
    void startersShouldOnlyAggregateDependencies() throws IOException {
        Path root = projectRoot().resolve("chaos-starters");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> starterDirectories = Files.list(root)) {
            for (Path starterDirectory : starterDirectories
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().endsWith("-starter"))
                    .toList()) {
                Path javaRoot = starterDirectory.resolve("src/main/java");
                if (!Files.exists(javaRoot)) {
                    continue;
                }
                for (Path javaFile : javaFiles(javaRoot)) {
                    violations.add(projectRoot().relativize(javaFile).toString());
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "starter 模块不应包含 Java 代码:\n" + String.join("\n", violations));
    }

    /**
     * 自动装配集中在唯一的 chaos-autoconfigure 模块，并使用 Spring Boot 3 的 AutoConfiguration.imports。
     *
     * <ul>
     *     <li>任何模块都不得包含 spring.factories；</li>
     *     <li>只有 chaos-autoconfigure 可以声明 AutoConfiguration.imports 与 {@code @AutoConfiguration} 类；</li>
     *     <li>imports 清单与源码中的 {@code @AutoConfiguration} 类必须一一对应，且不得位于根包；</li>
     *     <li>库模块与示例工程不得依赖 chaos-autoconfigure（库由 starter 聚合，示例只依赖 starter）。</li>
     * </ul>
     */
    @Test
    void autoConfigurationShouldLiveInSingleModuleWithBoot3Imports() throws IOException {
        Path root = projectRoot();
        Path autoconfigureModule = root.resolve("chaos-autoconfigure");
        String importsPath = "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains(File.separator + "target" + File.separator))
                    .toList()) {
                String relative = root.relativize(path).toString().replace(File.separatorChar, '/');
                String fileName = path.getFileName().toString();
                if (fileName.equals("spring.factories")) {
                    violations.add(relative + " 禁止使用 spring.factories");
                }
                if (fileName.equals("org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                        && !path.startsWith(autoconfigureModule)) {
                    violations.add(relative + " 自动装配清单只能位于 chaos-autoconfigure");
                }
                if (fileName.endsWith(".java") && relative.contains("/src/main/java/") && !path.startsWith(autoconfigureModule)
                        && Files.readString(path, StandardCharsets.UTF_8).contains("@AutoConfiguration")) {
                    violations.add(relative + " 自动装配类只能位于 chaos-autoconfigure");
                }
                if (fileName.equals("pom.xml") && !path.startsWith(autoconfigureModule)
                        && !relative.startsWith("chaos-starters/")
                        && !relative.equals("chaos-dependencies/pom.xml")
                        && Files.readString(path, StandardCharsets.UTF_8).contains("<artifactId>chaos-autoconfigure</artifactId>")) {
                    violations.add(relative + " 只有 starter 可以依赖 chaos-autoconfigure");
                }
            }
        }

        Path importsFile = autoconfigureModule.resolve(importsPath);
        Set<String> declared = new TreeSet<>();
        if (Files.exists(importsFile)) {
            Files.readAllLines(importsFile, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(declared::add);
        } else {
            violations.add("chaos-autoconfigure 缺少 AutoConfiguration.imports");
        }

        Path sourceRoot = autoconfigureModule.resolve("src/main/java");
        Set<String> annotated = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(sourceRoot)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String content = Files.readString(source, StandardCharsets.UTF_8);
                if (Pattern.compile("(?m)^@AutoConfiguration\\b").matcher(content).find()) {
                    String className = sourceRoot.relativize(source).toString()
                            .replace(File.separatorChar, '.')
                            .replaceAll("\\.java$", "");
                    annotated.add(className);
                    if (className.substring(0, className.lastIndexOf('.')).equals("com.michael.chaos.autoconfigure")) {
                        violations.add(className + " 自动装配类必须位于 com.michael.chaos.autoconfigure.<feature> 子包");
                    }
                }
            }
        }
        if (!declared.equals(annotated)) {
            Set<String> missing = new TreeSet<>(annotated);
            missing.removeAll(declared);
            Set<String> stale = new TreeSet<>(declared);
            stale.removeAll(annotated);
            violations.add("AutoConfiguration.imports 与 @AutoConfiguration 类不一致，未登记=" + missing + "，失效=" + stale);
        }

        assertTrue(violations.isEmpty(), () -> "自动装配结构不符合约定:\n" + String.join("\n", violations));
    }

    /**
     * 工程内禁止重新出现 package-info.java，避免和显式删除要求冲突。
     */
    @Test
    void packageInfoFilesShouldNotExist() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(projectRoot())) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> Objects.equals(path.getFileName().toString(), "package-info.java"))
                    .map(path -> projectRoot().relativize(path).toString())
                    .forEach(violations::add);
        }

        assertTrue(violations.isEmpty(), () -> "工程内不应包含 package-info.java:\n" + String.join("\n", violations));
    }

    /**
     * 安全领域 Redis 装配应先于安全自动装配，保证 Redis JWT 黑名单服务优先注册。
     */
    @Test
    void redisAutoConfigurationShouldRunBeforeSecurityAutoConfiguration() throws IOException {
        Path redisAutoConfiguration = projectRoot().resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/security/redis/ChaosSecurityRedisAutoConfiguration.java"
        );
        String annotation = autoConfigurationAnnotation(Files.readString(redisAutoConfiguration, StandardCharsets.UTF_8));

        assertTrue(
                annotation.contains("beforeName")
                        && annotation.contains("\"com.michael.chaos.autoconfigure.security.ChaosSecurityAutoConfiguration\""),
                "Redis 自动装配必须先于安全自动装配，避免默认 NoopJwtRevocationService 抢先注册"
        );
    }

    /**
     * JWT 互踢索引必须保存可撤销 token 的会话摘要。
     */
    @Test
    void authorizationKickoutShouldIndexRevocableSessionSummary() throws IOException {
        Path session = projectRoot().resolve(
                "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/kickout/AuthorizationSession.java"
        );
        String source = Files.readString(session, StandardCharsets.UTF_8);

        assertTrue(
                source.contains("accessTokenId")
                        && source.contains("accessTokenExpiresAt")
                        && source.contains("deviceId")
                        && source.contains("userAgent"),
                "授权互踢索引必须保存 access token 撤销标识、过期时间、设备和 User-Agent"
        );
    }

    /**
     * 授权互踢必须支持设备维度。
     */
    @Test
    void authorizationKickoutShouldSupportDeviceScope() throws IOException {
        Path properties = projectRoot().resolve(
                "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/core/ChaosAuthorizationProperties.java"
        );
        Path registry = projectRoot().resolve(
                "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/kickout/AuthorizationSessionRegistry.java"
        );
        Path converter = projectRoot().resolve(
                "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/grant/ChaosGrantAuthenticationConverter.java"
        );

        assertTrue(
                Files.readString(properties, StandardCharsets.UTF_8).contains("DEVICE"),
                "授权互踢范围必须包含 DEVICE"
        );
        assertTrue(
                Files.readString(registry, StandardCharsets.UTF_8).contains("findByPrincipalClientAndDevice"),
                "授权会话索引必须支持用户、客户端和设备维度查询"
        );
        assertTrue(
                Files.readString(converter, StandardCharsets.UTF_8).contains("X-Device-Id"),
                "token 请求转换器必须支持 X-Device-Id"
        );
    }

    /**
     * 本地授权会话索引必须有容量上限，避免高基数登录无限占用内存。
     */
    @Test
    void inMemoryAuthorizationSessionRegistryShouldHaveCapacityLimit() throws IOException {
        Path properties = projectRoot().resolve(
                "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/core/ChaosAuthorizationProperties.java"
        );
        Path registry = projectRoot().resolve(
                "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/kickout/InMemoryAuthorizationSessionRegistry.java"
        );
        Path autoConfiguration = projectRoot().resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/authorization/ChaosAuthorizationAutoConfiguration.java"
        );

        assertTrue(
                Files.readString(properties, StandardCharsets.UTF_8).contains("maxLocalSessions = 10_000"),
                "授权互踢本地索引必须提供 max-local-sessions 默认上限"
        );
        assertTrue(
                Files.readString(registry, StandardCharsets.UTF_8).contains("capacity exceeded")
                        && Files.readString(registry, StandardCharsets.UTF_8).contains("sessions.size() >= maxSessions"),
                "InMemoryAuthorizationSessionRegistry 必须在超过容量时拒绝新会话"
        );
        assertTrue(
                Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("getMaxLocalSessions()"),
                "授权自动装配必须把 max-local-sessions 传给本地会话索引"
        );
    }

    /**
     * 授权配置的嵌套对象和关键枚举必须具备空值保护，避免配置绑定后自动装配 NPE。
     */
    @Test
    void authorizationPropertiesShouldGuardNullNestedAndEnumValues() throws IOException {
        Path properties = projectRoot().resolve(
                "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/core/ChaosAuthorizationProperties.java"
        );
        String source = Files.readString(properties, StandardCharsets.UTF_8);

        assertTrue(
                source.contains("@NotNull(message = \"chaos.authorization.token must not be null\")")
                        && source.contains("this.token = token == null ? new Token() : token")
                        && source.contains("this.kickout = kickout == null ? new Kickout() : kickout")
                        && source.contains("this.client = client == null ? new Client() : client"),
                "授权配置嵌套对象必须有 @NotNull 校验和 setter 空值默认保护"
        );
        assertTrue(
                source.contains("@NotNull(message = \"chaos.authorization.token.type must not be null\")")
                        && source.contains("this.type = type == null ? TokenType.JWT : type")
                        && source.contains("this.scope = scope == null ? Scope.CLIENT : scope")
                        && source.contains("this.sessionRegistryType = sessionRegistryType == null ? SessionRegistryType.AUTO : sessionRegistryType")
                        && source.contains("this.storeType = storeType == null ? ClientStoreType.MEMORY : storeType"),
                "授权配置的关键枚举必须有 @NotNull 校验和安全默认值"
        );
    }

    /**
     * 授权服务器自动装配必须使用 Spring Authorization Server 1.5 推荐的新配置入口。
     */
    @Test
    void authorizationServerShouldUseCurrentSpringAuthorizationServerApi() throws IOException {
        Path autoConfiguration = projectRoot().resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/authorization/ChaosAuthorizationAutoConfiguration.java"
        );
        Path authorizationTests = projectRoot().resolve(
                "chaos-security/chaos-authorization/src/test/java"
        );
        String autoConfigurationSource = Files.readString(autoConfiguration, StandardCharsets.UTF_8);
        List<String> passwordGrantViolations = new ArrayList<>();
        for (Path javaFile : javaFiles(authorizationTests)) {
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            if (source.contains("AuthorizationGrantType.PASSWORD")) {
                passwordGrantViolations.add(projectRoot().relativize(javaFile).toString());
            }
        }

        assertTrue(
                autoConfigurationSource.contains("OAuth2AuthorizationServerConfigurer.authorizationServer()")
                        && autoConfigurationSource.contains(".with(authorizationServerConfigurer,")
                        && !autoConfigurationSource.contains("applyDefaultSecurity"),
                "授权服务器自动装配必须使用 OAuth2AuthorizationServerConfigurer.authorizationServer()，禁止回退到已废弃的 applyDefaultSecurity"
        );
        assertTrue(
                passwordGrantViolations.isEmpty(),
                () -> "测试代码不得使用已废弃的 AuthorizationGrantType.PASSWORD，应使用 ChaosAuthorizationGrantTypes.PASSWORD:\n"
                        + String.join("\n", passwordGrantViolations)
        );
    }

    /**
     * Gateway JWT 校验开启但没有 decoder 时必须拒绝请求。
     */
    @Test
    void gatewayJwtValidationShouldFailClosedWithoutDecoder() throws IOException {
        Path filter = projectRoot().resolve(
                "chaos-gateway/chaos-gateway/src/main/java/com/michael/chaos/gateway/filter/JwtAuthenticationGatewayFilter.java"
        );
        String source = Files.readString(filter, StandardCharsets.UTF_8);

        assertTrue(
                source.contains("if (jwtDecoder == null)") && source.contains("return unauthorized(exchange,"),
                "Gateway JWT 校验开启但没有 decoder 时必须 fail closed"
        );
    }

    /**
     * Gateway 默认白名单不得暴露 Prometheus 指标。
     */
    @Test
    void gatewayDefaultWhitelistShouldNotExposePrometheus() throws IOException {
        Path properties = projectRoot().resolve(
                "chaos-gateway/chaos-gateway/src/main/java/com/michael/chaos/gateway/config/ChaosGatewayProperties.java"
        );
        String source = Files.readString(properties, StandardCharsets.UTF_8);

        assertTrue(
                source.contains("List.of(\"/actuator/health\")")
                        && !source.contains("List.of(\"/actuator/health\", \"/actuator/prometheus\")"),
                "Gateway 默认白名单只能包含健康检查，Prometheus 应由显式配置或内网保护"
        );
    }

    /**
     * Resource Server 默认必须关闭 HTTP Basic 并使用无状态 session。
     */
    @Test
    void resourceServerShouldBeStatelessAndDisableHttpBasicByDefault() throws IOException {
        Path properties = projectRoot().resolve(
                "chaos-security/chaos-security/src/main/java/com/michael/chaos/security/config/ChaosSecurityProperties.java"
        );
        Path autoConfiguration = projectRoot().resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/security/ChaosSecurityAutoConfiguration.java"
        );
        String propertiesSource = Files.readString(properties, StandardCharsets.UTF_8);
        String autoConfigurationSource = Files.readString(autoConfiguration, StandardCharsets.UTF_8);

        assertTrue(
                propertiesSource.contains("httpBasicEnabled = false"),
                "Resource Server 默认必须关闭 HTTP Basic"
        );
        assertTrue(
                propertiesSource.contains("permitAll = {\"/actuator/health\"}")
                        && !propertiesSource.contains("/v3/api-docs/**")
                        && !propertiesSource.contains("/swagger-ui/**"),
                "Resource Server 默认匿名白名单只能包含健康检查，OpenAPI/Swagger 必须显式配置开放"
        );
        assertTrue(
                autoConfigurationSource.contains("SessionCreationPolicy.STATELESS")
                        && autoConfigurationSource.contains("http.httpBasic(AbstractHttpConfigurer::disable)"),
                "安全自动装配必须使用无状态 session，并在默认配置下禁用 HTTP Basic"
        );
    }

    /**
     * Redis/reference token 必须支持 opaque introspection 鉴权。
     */
    @Test
    void referenceTokenShouldSupportOpaqueIntrospection() throws IOException {
        Path securityProperties = projectRoot().resolve(
                "chaos-security/chaos-security/src/main/java/com/michael/chaos/security/config/ChaosSecurityProperties.java"
        );
        Path securityAutoConfiguration = projectRoot().resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/security/ChaosSecurityAutoConfiguration.java"
        );
        Path gatewayFilter = projectRoot().resolve(
                "chaos-gateway/chaos-gateway/src/main/java/com/michael/chaos/gateway/filter/OpaqueTokenAuthenticationGatewayFilter.java"
        );
        Path authorizationCustomizer = projectRoot().resolve(
                "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/token/ChaosTokenCustomizer.java"
        );

        assertTrue(
                Files.readString(securityProperties, StandardCharsets.UTF_8).contains("OPAQUE"),
                "资源服务器必须支持 OPAQUE token 类型"
        );
        assertTrue(
                Files.readString(securityAutoConfiguration, StandardCharsets.UTF_8).contains("opaqueToken("),
                "安全自动装配必须支持 opaque token introspection"
        );
        assertTrue(Files.exists(gatewayFilter), "Gateway 必须提供 opaque token introspection 鉴权过滤器");
        assertTrue(
                Files.readString(authorizationCustomizer, StandardCharsets.UTF_8).contains("customizeOpaque"),
                "授权服务器必须把用户、租户、角色和权限写入 reference token claim"
        );
    }

    /**
     * 安全核心必须提供通用 RBAC/ABAC 授权模型，并保持 @Permission 兼容该模型。
     */
    @Test
    void securityCoreShouldProvideRbacAndAbacAuthorizationModel() throws IOException {
        Path root = projectRoot();
        Path accessRoot = root.resolve("chaos-security/chaos-security-api/src/main/java/com/michael/chaos/security/api/access");
        Path dataScopeRoot = root.resolve("chaos-security/chaos-security-api/src/main/java/com/michael/chaos/security/api/datascope");
        Path permissionService = root.resolve(
                "chaos-security/chaos-security/src/main/java/com/michael/chaos/security/permission/DefaultPermissionCheckService.java"
        );
        Path dataScopeProvider = root.resolve(
                "chaos-data/chaos-mybatis/src/main/java/com/michael/chaos/mybatis/datascope/DataScopeProvider.java"
        );
        Path dataScopeAuthorizationService = root.resolve(
                "chaos-security/chaos-security-api/src/main/java/com/michael/chaos/security/api/datascope/DataScopeAuthorizationService.java"
        );
        Path autoConfiguration = root.resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/security/ChaosSecurityAutoConfiguration.java"
        );

        assertTrue(Files.exists(accessRoot.resolve("AuthorizationRequest.java")), "安全核心必须提供 AuthorizationRequest");
        assertTrue(Files.exists(accessRoot.resolve("AuthorizationDecision.java")), "安全核心必须提供 AuthorizationDecision");
        assertTrue(Files.exists(accessRoot.resolve("RbacAuthorizationPolicy.java")), "安全核心必须提供 RBAC 策略");
        assertTrue(Files.exists(accessRoot.resolve("AbacAuthorizationPolicy.java")), "安全核心必须提供 ABAC 策略");
        assertTrue(Files.exists(dataScopeRoot.resolve("DataScopeRequest.java")), "安全核心必须提供数据权限请求上下文");
        assertTrue(Files.exists(dataScopeAuthorizationService), "安全核心必须提供数据权限授权服务");
        assertTrue(
                Files.readString(permissionService, StandardCharsets.UTF_8).contains("PermissionAuthorizationService"),
                "默认 @Permission 校验必须复用通用授权服务"
        );
        assertTrue(
                Files.readString(dataScopeProvider, StandardCharsets.UTF_8).contains("DataScopeRequest"),
                "MyBatis 数据权限 Provider 必须支持完整数据权限请求"
        );
        assertTrue(
                Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("AuthorizationManager")
                        && Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("RbacAuthorizationPolicy"),
                "安全自动装配必须注册默认 RBAC 策略和 AuthorizationManager"
        );
    }

    /**
     * 示例工程必须提供完整 auth-server、gateway、order-service 链路，并且只依赖 starter。
     */
    @Test
    void examplesShouldProvideRunnableServiceChainAndDependOnStartersOnly() throws IOException {
        Path examplesPom = projectRoot().resolve("chaos-examples/pom.xml");
        String modules = Files.readString(examplesPom, StandardCharsets.UTF_8);
        List<String> violations = new ArrayList<>();

        assertTrue(
                modules.contains("example-auth-server")
                        && modules.contains("example-gateway")
                        && modules.contains("example-order-service"),
                "示例工程必须包含 auth-server、gateway、order-service 三个模块"
        );

        for (Path pom : List.of(
                projectRoot().resolve("chaos-examples/example-auth-server/pom.xml"),
                projectRoot().resolve("chaos-examples/example-gateway/pom.xml"),
                projectRoot().resolve("chaos-examples/example-order-service/pom.xml"))) {
            String source = Files.readString(pom, StandardCharsets.UTF_8);
            if (source.contains("-autoconfigure")) {
                violations.add(projectRoot().relativize(pom).toString());
            }
        }

        assertTrue(violations.isEmpty(), () -> "示例工程不得直接依赖 autoconfigure:\n" + String.join("\n", violations));
    }

    /**
     * 数据权限空条件默认必须拒绝，避免权限服务异常时误放行。
     */
    @Test
    void dataScopeEmptyConditionShouldDenyByDefault() throws IOException {
        Path properties = projectRoot().resolve(
                "chaos-data/chaos-mybatis/src/main/java/com/michael/chaos/mybatis/tenant/ChaosMybatisProperties.java"
        );
        String source = Files.readString(properties, StandardCharsets.UTF_8);

        assertTrue(
                source.contains("emptyConditionBehavior = EmptyConditionBehavior.DENY"),
                "数据权限空条件默认策略必须是 DENY"
        );
    }

    /**
     * 多租户 SQL 改写在租户缺失时必须默认拒绝，避免绕过 Gateway 后误放行。
     */
    @Test
    void tenantMissingTenantShouldDenyByDefault() throws IOException {
        Path properties = projectRoot().resolve(
                "chaos-data/chaos-mybatis/src/main/java/com/michael/chaos/mybatis/tenant/ChaosMybatisProperties.java"
        );
        Path autoConfiguration = projectRoot().resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/mybatis/ChaosMybatisAutoConfiguration.java"
        );
        // 租户 SQL 改写逻辑已从自动装配匿名类抽取为 chaos-mybatis 中可单测的 ChaosTenantLineHandler。
        Path tenantLineHandler = projectRoot().resolve(
                "chaos-data/chaos-mybatis/src/main/java/com/michael/chaos/mybatis/tenant/ChaosTenantLineHandler.java"
        );
        String handlerSource = Files.readString(tenantLineHandler, StandardCharsets.UTF_8);

        assertTrue(
                Files.readString(properties, StandardCharsets.UTF_8)
                        .contains("missingTenantBehavior = MissingTenantBehavior.DENY"),
                "租户缺失默认策略必须是 DENY"
        );
        assertTrue(
                handlerSource.contains("MissingTenantBehavior.DENY")
                        // ChaosDiagnosticException 继承 IllegalStateException，并附带"问题 / 原因 / 怎么修"。
                        && (handlerSource.contains("throw new IllegalStateException")
                        || handlerSource.contains("throw new ChaosDiagnosticException")),
                "MyBatis 租户 SQL 改写必须在租户缺失且 DENY 时 fail-fast 拒绝"
        );
        assertTrue(
                Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("new ChaosTenantLineHandler("),
                "MyBatis 自动装配必须使用 ChaosTenantLineHandler 注册租户拦截器"
        );
    }

    /**
     * Gateway 黑名单必须支持 CIDR，客户端 IP 解析必须复用 chaos-core 的可信代理算法（与 Servlet 服务一致）。
     */
    @Test
    void gatewayBlacklistShouldSupportCidrAndForwardedIp() throws IOException {
        Path matcher = projectRoot().resolve(
                "chaos-foundation/chaos-core/src/main/java/com/michael/chaos/core/net/CidrMatcher.java"
        );
        Path coreResolver = projectRoot().resolve(
                "chaos-foundation/chaos-core/src/main/java/com/michael/chaos/core/net/ForwardedClientIpResolver.java"
        );
        Path resolver = projectRoot().resolve(
                "chaos-gateway/chaos-gateway/src/main/java/com/michael/chaos/gateway/filter/ClientIpResolver.java"
        );

        assertTrue(Files.exists(matcher), "Gateway 黑名单必须提供 CIDR 匹配能力");
        assertTrue(Files.exists(coreResolver), "客户端 IP 解析算法必须统一在 chaos-core 中实现");
        String resolverSource = Files.readString(resolver, StandardCharsets.UTF_8);
        assertTrue(resolverSource.contains("X-Forwarded-For"), "Gateway 客户端 IP 解析必须支持 X-Forwarded-For");
        assertTrue(resolverSource.contains("ForwardedClientIpResolver"), "Gateway 客户端 IP 解析必须复用 chaos-core 算法");
    }

    /**
     * Gateway 错误响应体的字段必须与 {@code Result} 记录完全一致。
     *
     * <p>网关是响应式栈、不依赖 chaos-web，无法复用 {@code Result}，只能手写同结构 JSON 模板。
     * 于是 {@code Result} 增删字段时网关会静默漂移，同一个调用方在"被网关拒绝"和"被服务拒绝"两种情况下
     * 拿到的响应结构不同。这里解析两边的字段名逐一比对。</p>
     *
     * <p>原实现只断言源码文本里出现过 {@code "traceId"} 这四个字符——一句注释就能让它通过，
     * 既不校验字段集合，也不校验 JSON 能否解析。</p>
     */
    @Test
    void gatewayErrorResponseShouldMatchResultShape() throws IOException {
        Set<String> resultFields = recordComponents(projectRoot().resolve(
                "chaos-web/src/main/java/com/michael/chaos/web/result/Result.java"));
        Set<String> gatewayFields = jsonTemplateFields(projectRoot().resolve(
                "chaos-gateway/chaos-gateway/src/main/java/com/michael/chaos/gateway/filter/GatewayErrorResponseWriter.java"));

        assertFalse(resultFields.isEmpty(), "未能解析出 Result 的记录组件");
        assertEquals(resultFields, gatewayFields,
                "Gateway 错误响应字段必须与 Result 完全一致，实际 Result=" + resultFields + "，Gateway=" + gatewayFields);
    }

    /**
     * 解析 record 的组件名。
     */
    private static Set<String> recordComponents(Path recordSource) throws IOException {
        String source = Files.readString(recordSource, StandardCharsets.UTF_8);
        Matcher header = Pattern.compile("public record \\w+<?[^>]*>?\\(([^)]*)\\)", Pattern.DOTALL).matcher(source);
        assertTrue(header.find(), "未找到 record 声明：" + recordSource);
        Set<String> components = new TreeSet<>();
        for (String parameter : header.group(1).split(",")) {
            String[] parts = parameter.trim().split("\\s+");
            if (parts.length >= 2) {
                components.add(parts[parts.length - 1]);
            }
        }
        return components;
    }

    /**
     * 解析 JSON 字符串模板中的字段名。
     */
    private static Set<String> jsonTemplateFields(Path writerSource) throws IOException {
        String source = Files.readString(writerSource, StandardCharsets.UTF_8);
        Matcher template = Pattern.compile("\\{(\"[a-zA-Z]+\":[^}]*)}").matcher(source);
        assertTrue(template.find(), "未找到 Gateway 错误响应 JSON 模板：" + writerSource);
        Set<String> fields = new TreeSet<>();
        Matcher field = Pattern.compile("\"([a-zA-Z][a-zA-Z0-9]*)\"\\s*:").matcher(template.group(1));
        while (field.find()) {
            fields.add(field.group(1));
        }
        return fields;
    }

    // 说明：原先这里还有 gatewayShouldProvideRateLimitFilter / gatewayShouldProvideFallbackExceptionHandler
    // 两个测试，断言方式是"文件存在"加"源码里出现过 RateLimit / Fallback 子串"——一句注释就能让它们通过，
    // 既不校验限流是否真的生效，也不校验降级是否真的返回 503。
    // 这两条约束已由 GatewayRateLimitFilterTest、GatewayFallbackExceptionHandlerTest、
    // GatewayErrorResponseWriterTest 以真实请求断言覆盖，这里不再重复一份更弱的版本。

    /**
     * Gateway Nacos 动态路由必须保持为可选扩展，避免核心 Gateway 强依赖 Nacos。
     */
    @Test
    void gatewayNacosRoutesShouldStayOptionalExtension() throws IOException {
        Path root = projectRoot();
        Path gatewaySourceRoot = root.resolve("chaos-gateway/chaos-gateway/src/main/java");
        Path gatewayNacosRoot = root.resolve("chaos-gateway/chaos-gateway-nacos");
        Path autoConfiguration = root.resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/gateway/nacos/ChaosGatewayNacosAutoConfiguration.java"
        );
        Path starter = root.resolve("chaos-starters/chaos-gateway-nacos-starter/pom.xml");
        List<String> violations = new ArrayList<>();

        for (Path javaFile : javaFiles(gatewaySourceRoot)) {
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            if (source.contains("com.alibaba.nacos") || source.contains("com.alibaba.cloud.nacos")) {
                violations.add(root.relativize(javaFile).toString());
            }
        }

        assertTrue(violations.isEmpty(), () -> "chaos-gateway 核心不得依赖 Nacos:\n" + String.join("\n", violations));
        assertTrue(Files.exists(gatewayNacosRoot), "必须提供独立 chaos-gateway-nacos 扩展模块");
        assertTrue(
                Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("NacosRouteDefinitionRepository")
                        && Files.readString(autoConfiguration, StandardCharsets.UTF_8)
                        .contains("beforeName = \"org.springframework.cloud.gateway.config.GatewayAutoConfiguration\""),
                "Gateway Nacos 自动装配必须注册 Nacos 路由仓库，并先于 Gateway 默认路由仓库执行"
        );
        assertTrue(
                Files.readString(starter, StandardCharsets.UTF_8).contains("spring-cloud-starter-alibaba-nacos-config")
                        && Files.readString(starter, StandardCharsets.UTF_8).contains("chaos-gateway-starter"),
                "Gateway Nacos starter 必须聚合 Gateway starter 和 Nacos Config starter"
        );
    }

    /**
     * 安全拒绝路径必须发布审计事件。
     */
    @Test
    void securityDeniedPathsShouldPublishAuditEvents() throws IOException {
        List<Path> sources = List.of(
                projectRoot().resolve(
                        "chaos-security/chaos-security/src/main/java/com/michael/chaos/security/permission/PermissionAspect.java"
                ),
                projectRoot().resolve(
                        "chaos-gateway/chaos-gateway/src/main/java/com/michael/chaos/gateway/filter/JwtAuthenticationGatewayFilter.java"
                ),
                projectRoot().resolve(
                        "chaos-gateway/chaos-gateway/src/main/java/com/michael/chaos/gateway/filter/BlacklistFilter.java"
                ),
                projectRoot().resolve(
                        "chaos-gateway/chaos-gateway/src/main/java/com/michael/chaos/gateway/filter/TenantGatewayFilter.java"
                )
        );
        List<String> violations = new ArrayList<>();

        for (Path sourceFile : sources) {
            String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
            if (!source.contains("AuditEventPublisher") || !source.contains(".publish(")) {
                violations.add(projectRoot().relativize(sourceFile).toString());
            }
        }

        assertTrue(violations.isEmpty(), () -> "安全拒绝路径缺少审计发布:\n" + String.join("\n", violations));
    }

    /**
     * 授权服务器关键 token 路径必须发布审计事件。
     */
    @Test
    void authorizationTokenPathsShouldPublishAuditEvents() throws IOException {
        List<Path> sources = List.of(
                projectRoot().resolve(
                        "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/grant/ChaosGrantAuthenticationProvider.java"
                ),
                projectRoot().resolve(
                        "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/token/ChaosRefreshTokenAuthenticationProvider.java"
                ),
                projectRoot().resolve(
                        "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/token/ChaosTokenRevocationAuthenticationProvider.java"
                ),
                projectRoot().resolve(
                        "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/kickout/IndexedAuthorizationKickoutService.java"
                )
        );
        List<String> violations = new ArrayList<>();

        for (Path sourceFile : sources) {
            String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
            if (!source.contains("AuditEventPublisher") || !source.contains(".publish(")) {
                violations.add(projectRoot().relativize(sourceFile).toString());
            }
        }

        assertTrue(violations.isEmpty(), () -> "授权 token 路径缺少审计发布:\n" + String.join("\n", violations));
    }

    /**
     * Redis 授权存储保存轮换 token 时必须清理旧 token 索引。
     */
    @Test
    void redisAuthorizationServiceShouldRemoveOldTokenIndexesBeforeSavingRotatedAuthorization() throws IOException {
        Path sourceFile = projectRoot().resolve(
                "chaos-security/chaos-security-redis/src/main/java/com/michael/chaos/security/redis/authorization/RedisOAuth2AuthorizationService.java"
        );
        String source = Files.readString(sourceFile, StandardCharsets.UTF_8);

        assertTrue(
                source.contains("OAuth2Authorization existing = findById(authorization.getId())")
                        && source.contains("deleteTokenIndex(existing.getRefreshToken())"),
                "Redis 授权存储保存轮换授权前必须清理旧 refresh token 索引"
        );
    }

    /**
     * JDBC 审计适配器必须保持为 audit 端口的基础设施实现。
     */
    @Test
    void auditJdbcShouldProvidePersistenceAdapter() throws IOException {
        Path publisher = projectRoot().resolve(
                "chaos-audit/chaos-audit-jdbc/src/main/java/com/michael/chaos/audit/jdbc/JdbcAuditEventPublisher.java"
        );
        Path schema = projectRoot().resolve(
                "chaos-audit/chaos-audit-jdbc/src/main/resources/db/chaos-audit-schema.sql"
        );
        Path starter = projectRoot().resolve("chaos-starters/chaos-audit-jdbc-starter/pom.xml");
        String source = Files.readString(publisher, StandardCharsets.UTF_8);

        assertTrue(
                source.contains("implements AuditEventPublisher") && source.contains("JdbcOperations"),
                "JDBC 审计发布器必须实现 AuditEventPublisher 端口并通过 Spring JDBC 落库"
        );
        assertTrue(Files.exists(schema), "JDBC 审计模块必须提供建表脚本");
        assertTrue(
                Files.readString(schema, StandardCharsets.UTF_8).contains("chaos_audit_event"),
                "JDBC 审计建表脚本必须创建 chaos_audit_event"
        );
        assertTrue(
                Files.readString(starter, StandardCharsets.UTF_8).contains("<artifactId>chaos-autoconfigure</artifactId>")
                        && Files.readString(starter, StandardCharsets.UTF_8).contains("<artifactId>chaos-audit-jdbc</artifactId>"),
                "JDBC 审计 starter 必须聚合统一自动装配模块与 JDBC 审计库"
        );
    }

    /**
     * JDBC 审计自动装配必须先于默认日志审计自动装配，并受总开关控制。
     */
    @Test
    void auditJdbcAutoConfigurationShouldRunBeforeDefaultAuditAutoConfiguration() throws IOException {
        Path autoConfiguration = projectRoot().resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/audit/jdbc/ChaosAuditJdbcAutoConfiguration.java"
        );
        String source = Files.readString(autoConfiguration, StandardCharsets.UTF_8);
        String annotation = autoConfigurationAnnotation(source);

        assertTrue(
                annotation.contains("beforeName")
                        && annotation.contains("\"com.michael.chaos.autoconfigure.audit.ChaosAuditAutoConfiguration\""),
                "JDBC 审计自动装配必须先于默认日志审计自动装配"
        );
        assertTrue(
                source.contains("prefix = \"chaos.audit\"")
                        && source.contains("prefix = \"chaos.audit.jdbc\""),
                "JDBC 审计自动装配必须同时受全局审计开关和 JDBC 开关控制"
        );
    }

    /**
     * 租户核心模块必须保持为无基础设施依赖的治理契约。
     */
    @Test
    void tenantModuleShouldProvideLifecycleContracts() throws IOException {
        Path tenantRoot = projectRoot().resolve("chaos-tenant/src/main/java/com/michael/chaos/tenant");
        Path provider = tenantRoot.resolve("TenantStatusProvider.java");
        Path status = tenantRoot.resolve("TenantStatus.java");
        Path context = tenantRoot.resolve("TenantContext.java");

        assertTrue(Files.exists(provider), "租户模块必须提供 TenantStatusProvider 端口");
        assertTrue(Files.readString(status, StandardCharsets.UTF_8).contains("FROZEN"), "租户状态必须支持冻结状态");
        assertTrue(
                Files.readString(context, StandardCharsets.UTF_8).contains("RequestContext.setTenantId"),
                "租户上下文必须和 RequestContext 租户 ID 保持一致"
        );
    }

    /**
     * Gateway 必须在入口处支持租户状态 fail-closed。
     */
    @Test
    void gatewayShouldValidateTenantStatusAtEntrance() throws IOException {
        Path filter = projectRoot().resolve(
                "chaos-gateway/chaos-gateway/src/main/java/com/michael/chaos/gateway/filter/TenantGatewayFilter.java"
        );
        Path autoConfiguration = projectRoot().resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/gateway/ChaosGatewayAutoConfiguration.java"
        );

        assertTrue(Files.exists(filter), "Gateway 必须提供租户状态过滤器");
        assertTrue(
                Files.readString(filter, StandardCharsets.UTF_8).contains("TenantAccessValidator")
                        && Files.readString(filter, StandardCharsets.UTF_8).contains("GatewayErrorResponseWriter.forbidden"),
                "Gateway 租户状态过滤器必须通过 TenantAccessValidator 决策并用 403 拒绝"
        );
        assertTrue(
                Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("TenantGatewayFilter"),
                "Gateway 自动装配必须注册租户状态过滤器"
        );
    }

    /**
     * MQ 核心必须定义可靠消息端口，并保持不依赖具体 MQ SDK。
     */
    @Test
    void mqCoreShouldProvideReliableMessageContracts() throws IOException {
        Path reliableRoot = projectRoot().resolve("chaos-mq/chaos-mq/src/main/java/com/michael/chaos/mq/reliable");
        Path repository = reliableRoot.resolve("OutboxMessageRepository.java");
        Path dispatcher = reliableRoot.resolve("ReliableMessageDispatcher.java");
        Path status = reliableRoot.resolve("ReliableMessageStatus.java");

        assertTrue(Files.exists(repository), "MQ 核心必须提供 outbox 仓储端口");
        assertTrue(Files.exists(dispatcher), "MQ 核心必须提供可靠消息派发器");
        assertTrue(
                Files.readString(status, StandardCharsets.UTF_8).contains("DEAD_LETTER"),
                "可靠消息状态必须包含死信状态"
        );
    }

    /**
     * MQ 必须提供 JDBC outbox 适配器，并保持核心契约不依赖 JDBC。
     */
    @Test
    void mqShouldProvideJdbcOutboxAdapterOutsideCore() throws IOException {
        Path root = projectRoot();
        Path mqParent = root.resolve("chaos-mq/pom.xml");
        Path repositoryPort = root.resolve(
                "chaos-mq/chaos-mq/src/main/java/com/michael/chaos/mq/reliable/OutboxMessageRepository.java"
        );
        Path dispatcher = root.resolve(
                "chaos-mq/chaos-mq/src/main/java/com/michael/chaos/mq/reliable/ReliableMessageDispatcher.java"
        );
        Path jdbcRepository = root.resolve(
                "chaos-mq/chaos-mq-jdbc/src/main/java/com/michael/chaos/mq/jdbc/JdbcOutboxMessageRepository.java"
        );
        Path schema = root.resolve("chaos-mq/chaos-mq-jdbc/src/main/resources/db/chaos-mq-outbox-schema.sql");
        Path autoConfiguration = root.resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/mq/ChaosMqAutoConfiguration.java"
        );
        Path starter = root.resolve("chaos-starters/chaos-mq-starter/pom.xml");

        assertTrue(
                Files.readString(mqParent, StandardCharsets.UTF_8).contains("chaos-mq-jdbc"),
                "MQ 聚合模块必须包含 chaos-mq-jdbc"
        );
        assertTrue(
                Files.readString(repositoryPort, StandardCharsets.UTF_8).contains("claimDueMessages"),
                "outbox 仓储端口必须提供 claimDueMessages 以支持多实例派发"
        );
        assertTrue(
                Files.readString(dispatcher, StandardCharsets.UTF_8).contains("repository.claimDueMessages"),
                "可靠消息派发器必须通过 claimDueMessages 获取消息"
        );
        assertTrue(Files.exists(jdbcRepository), "MQ 必须提供 JDBC outbox 仓储实现");
        assertTrue(
                Files.readString(jdbcRepository, StandardCharsets.UTF_8).contains("implements OutboxMessageRepository")
                        && Files.readString(jdbcRepository, StandardCharsets.UTF_8).contains("JdbcOperations")
                        && Files.readString(jdbcRepository, StandardCharsets.UTF_8).contains("claim_owner"),
                "JDBC outbox 仓储必须实现 OutboxMessageRepository、通过 JdbcOperations 落库并支持 claim_owner"
        );
        assertTrue(
                Files.readString(schema, StandardCharsets.UTF_8).contains("chaos_mq_outbox")
                        && Files.readString(schema, StandardCharsets.UTF_8).contains("claimed_at")
                        && Files.readString(schema, StandardCharsets.UTF_8).contains("claim_owner"),
                "MQ JDBC 模块必须提供 outbox 建表脚本和 claim 字段"
        );
        assertTrue(
                Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("JdbcOutboxMessageRepository")
                        && Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("ConditionalOnBean(JdbcOperations.class)"),
                "MQ 自动装配必须在存在 JdbcOperations 时注册 JDBC outbox"
        );
        assertTrue(
                Files.readString(starter, StandardCharsets.UTF_8).contains("chaos-mq-jdbc"),
                "MQ starter 必须聚合 chaos-mq-jdbc"
        );
    }

    /**
     * 配置属性类必须开启 Bean Validation 校验。
     */
    @Test
    void configurationPropertiesShouldBeValidated() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(projectRoot())) {
            for (Path sourceFile : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Properties.java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .toList()) {
                String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
                if (source.contains("@ConfigurationProperties") && !source.contains("@Validated")) {
                    violations.add(projectRoot().relativize(sourceFile).toString());
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "配置属性类必须添加 @Validated:\n" + String.join("\n", violations));
    }

    /**
     * 嵌套配置属性必须显式拒绝 null，并在 setter 中恢复默认对象，避免自动装配阶段 NPE。
     */
    @Test
    void nestedConfigurationPropertiesShouldGuardNullValues() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(projectRoot())) {
            for (Path sourceFile : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Properties.java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .toList()) {
                String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
                if (!source.contains("@ConfigurationProperties")) {
                    continue;
                }
                List<String> lines = Files.readAllLines(sourceFile, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String annotation = lines.get(i).trim();
                    if (!Objects.equals(annotation, "@Valid") && !annotation.startsWith("@Valid(")) {
                        continue;
                    }
                    if (i + 2 >= lines.size() || !lines.get(i + 1).contains("@NotNull")) {
                        violations.add(projectRoot().relativize(sourceFile) + ":" + (i + 1)
                                + " -> @Valid 嵌套配置字段必须同时声明 @NotNull");
                        continue;
                    }
                    String fieldLine = lines.get(i + 2).trim();
                    NestedField nestedField = nestedField(fieldLine);
                    if (nestedField == null) {
                        continue;
                    }
                    String directNullAssignment = "this." + nestedField.name() + " = " + nestedField.name() + ";";
                    String nullDefaultAssignment = "this." + nestedField.name() + " = " + nestedField.name()
                            + " == null ? new " + nestedField.type() + "() : " + nestedField.name() + ";";
                    if (source.contains(directNullAssignment) && !source.contains(nullDefaultAssignment)) {
                        violations.add(projectRoot().relativize(sourceFile) + " -> set"
                                + Character.toUpperCase(nestedField.name().charAt(0)) + nestedField.name().substring(1)
                                + " 必须在参数为 null 时恢复默认对象");
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "嵌套配置属性缺少空值保护:\n" + String.join("\n", violations));
    }

    /**
     * 承载配置属性的模块必须输出配置元数据。
     *
     * <p>{@code @ConfigurationProperties} 类既可能位于 autoconfigure 模块，也可能位于 chaos-web、chaos-security
     * 等库模块。configuration processor 只会为当前编译模块中的类生成元数据，所以必须在“类所在的模块”引入，
     * 而不是只在引用它的 autoconfigure 模块引入。</p>
     */
    @Test
    void modulesWithConfigurationPropertiesShouldUseConfigurationProcessor() throws IOException {
        List<String> violations = new ArrayList<>();
        Path root = projectRoot();
        for (Path pomFile : pomFiles(root)) {
            Path module = pomFile.getParent();
            String relativeModule = root.relativize(module).toString();
            if (relativeModule.startsWith("chaos-examples")) {
                continue;
            }
            Path javaRoot = module.resolve("src/main/java");
            boolean hasProperties = Files.exists(javaRoot) && javaFiles(javaRoot).stream()
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .anyMatch(source -> source.contains("@ConfigurationProperties"));
            if (!hasProperties) {
                continue;
            }
            Path pom = module.resolve("pom.xml");
            if (!Files.readString(pom, StandardCharsets.UTF_8).contains("spring-boot-configuration-processor")) {
                violations.add(projectRoot().relativize(pom).toString());
            }
        }

        assertTrue(violations.isEmpty(), () -> "承载 @ConfigurationProperties 的模块必须引入 configuration processor:\n" + String.join("\n", violations));
    }

    /**
     * Trace 传播必须兼容 W3C Trace Context。
     */
    @Test
    void tracePropagationShouldSupportW3cHeaders() throws IOException {
        Path headers = projectRoot().resolve("chaos-foundation/chaos-core/src/main/java/com/michael/chaos/core/constant/ChaosHeaders.java");
        Path traceHeaders = projectRoot().resolve(
                "chaos-observability/chaos-trace/src/main/java/com/michael/chaos/trace/TraceHeaders.java"
        );
        Path observationFilter = projectRoot().resolve(
                "chaos-observability/chaos-trace/src/main/java/com/michael/chaos/trace/monitor/ChaosObservationFilter.java"
        );
        Path webFilter = projectRoot().resolve("chaos-web/src/main/java/com/michael/chaos/web/filter/TraceFilter.java");
        Path gatewayFilter = projectRoot().resolve("chaos-gateway/chaos-gateway/src/main/java/com/michael/chaos/gateway/filter/GatewayTraceFilter.java");

        assertTrue(
                Files.readString(headers, StandardCharsets.UTF_8).contains("TRACEPARENT")
                        && Files.readString(headers, StandardCharsets.UTF_8).contains("TRACESTATE")
                        && Files.readString(headers, StandardCharsets.UTF_8).contains("BAGGAGE"),
                "框架标准请求头必须包含 W3C traceparent、tracestate 和 baggage"
        );
        assertTrue(
                Files.readString(traceHeaders, StandardCharsets.UTF_8).contains("ChaosHeaders.TRACEPARENT"),
                "TraceHeaders 出站传播必须包含 traceparent"
        );
        assertTrue(
                Files.readString(observationFilter, StandardCharsets.UTF_8).contains("ObservationFilter"),
                "Monitor 模块必须提供 Micrometer Observation 兼容过滤器"
        );
        assertTrue(
                Files.readString(webFilter, StandardCharsets.UTF_8).contains("ChaosHeaders.TRACEPARENT"),
                "Web 入口过滤器必须解析 traceparent"
        );
        assertTrue(
                Files.readString(gatewayFilter, StandardCharsets.UTF_8).contains("W3cTraceContext"),
                "Gateway 入口过滤器必须支持 W3C Trace Context"
        );
    }

    /**
     * Testcontainers 集成测试必须通过独立 profile 隔离，普通单测不得启动 Docker。
     */
    @Test
    void testcontainersShouldRunOnlyInIntegrationProfile() throws IOException {
        Path parentPom = projectRoot().resolve("pom.xml");
        String parent = Files.readString(parentPom, StandardCharsets.UTF_8);
        List<String> violations = new ArrayList<>();

        assertTrue(
                parent.contains("<id>chaos-integration-test</id>")
                        && parent.contains("src/integration-test/java")
                        && parent.contains("maven-failsafe-plugin"),
                "父 POM 必须提供 chaos-integration-test profile 并通过 Failsafe 运行集成测试"
        );

        try (Stream<Path> paths = Files.walk(projectRoot())) {
            for (Path sourceFile : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/test/java/"))
                    .filter(path -> !Objects.equals(path.getFileName().toString(), "LayerBoundaryArchitectureTest.java"))
                    .toList()) {
                String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
                if (source.contains("org.testcontainers")) {
                    violations.add(projectRoot().relativize(sourceFile).toString());
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "普通单元测试不得直接依赖 Testcontainers:\n" + String.join("\n", violations));
    }

    /**
     * 发布治理必须提供独立 release profile，避免普通开发阶段承担签名和 API 对比成本。
     */
    @Test
    void releaseGovernanceShouldProvideReleaseProfileAndApiCompatibilityGate() throws IOException {
        Path parentPom = projectRoot().resolve("pom.xml");
        String parent = Files.readString(parentPom, StandardCharsets.UTF_8);

        assertTrue(
                parent.contains("<id>chaos-release</id>")
                        && parent.contains("<dependencyConvergence/>")
                        && parent.contains("<requirePluginVersions/>"),
                "父 POM 必须提供 chaos-release profile，并启用依赖收敛和插件版本检查"
        );
        assertTrue(
                parent.contains("maven-source-plugin")
                        && parent.contains("maven-javadoc-plugin")
                        && parent.contains("maven-gpg-plugin"),
                "发布 profile 必须附加 source jar、javadoc jar 和 GPG 签名入口"
        );
        assertTrue(
                parent.contains("japicmp-maven-plugin")
                        && parent.contains("chaos.api.check.skip")
                        && parent.contains("chaos.release.compareVersion"),
                "父 POM 必须提供可配置的 API 兼容检查入口"
        );
    }

    /**
     * 公共 BOM 必须与真实模块双向一致。
     *
     * <p>正向：所有对外 artifact 都要在 BOM 中登记，避免业务方手写内部模块版本；
     * 反向：BOM 不得声明仓库中不存在的 artifact（例如历史规划但未落地的模块），否则业务方 import 后解析失败。</p>
     */
    @Test
    void publicBomShouldManageAllPublishedArtifacts() throws IOException {
        Path root = projectRoot();
        Set<String> publishedArtifacts = publishedArtifactIds(root);
        Set<String> bomArtifacts = chaosArtifactsManagedBy(root.resolve("chaos-dependencies/pom.xml"));

        List<String> missing = publishedArtifacts.stream()
                .filter(artifactId -> !bomArtifacts.contains(artifactId))
                .toList();
        List<String> unknown = bomArtifacts.stream()
                .filter(artifactId -> !publishedArtifacts.contains(artifactId))
                .toList();

        assertTrue(missing.isEmpty(), () -> "chaos-dependencies BOM 缺少对外 artifact:\n" + String.join("\n", missing));
        assertTrue(unknown.isEmpty(), () -> "chaos-dependencies BOM 声明了不存在或不发布的 artifact:\n" + String.join("\n", unknown));
    }


    /**
     * 业务应用推荐的 chaos-boot-parent 必须只提供依赖版本与应用构建约定，不得夹带框架自身的发布治理。
     *
     * <ul>
     *     <li>以 chaos-dependencies 为 parent：依赖版本仍只有一份来源；</li>
     *     <li>不继承 chaos-parent：japicmp、JaCoCo、GPG、enforcer 等框架治理插件不能进入业务应用构建；</li>
     *     <li>预置编译参数、spring-boot repackage，flatten 只对自身生效（inherited=false）；</li>
     *     <li>BOM 自身的 flatten 同样不可继承，否则会通过 chaos-boot-parent 传给业务应用。</li>
     * </ul>
     */
    @Test
    void bootParentShouldOnlyProvideApplicationBuildConventions() throws IOException {
        Path root = projectRoot();
        // 去掉 XML 注释：chaos-boot-parent 的注释里有 <parent> 用法示例，不能参与结构判断。
        String bootParent = Files.readString(root.resolve("chaos-boot-parent/pom.xml"), StandardCharsets.UTF_8)
                .replaceAll("(?s)<!--.*?-->", "");
        String bom = Files.readString(root.resolve("chaos-dependencies/pom.xml"), StandardCharsets.UTF_8)
                .replaceAll("(?s)<!--.*?-->", "");
        String parentBlock = bootParent.substring(bootParent.indexOf("<parent>"), bootParent.indexOf("</parent>"));
        String bootParentFlatten = bootParent.substring(bootParent.indexOf("<artifactId>flatten-maven-plugin</artifactId>"));
        String bomFlatten = bom.substring(bom.indexOf("<artifactId>flatten-maven-plugin</artifactId>"));

        assertTrue(parentBlock.contains("<artifactId>chaos-dependencies</artifactId>"),
                "chaos-boot-parent 必须以 chaos-dependencies 为 parent");
        assertTrue(!bootParent.contains("<dependencyManagement>") && !bootParent.contains("<dependencies>"),
                "chaos-boot-parent 不得声明依赖或重复维护版本");
        assertTrue(Stream.of("japicmp", "jacoco", "maven-gpg-plugin", "maven-enforcer-plugin", "central-publishing")
                        .noneMatch(bootParent::contains),
                "chaos-boot-parent 不得包含框架发布治理插件");
        assertTrue(bootParent.contains("<parameters>true</parameters>")
                        && bootParent.contains("<goal>repackage</goal>")
                        && bootParent.contains("<release>${java.version}</release>"),
                "chaos-boot-parent 必须预置 -parameters、Java release 与 spring-boot repackage");
        assertTrue(bootParentFlatten.substring(0, bootParentFlatten.indexOf("</plugin>")).contains("<inherited>false</inherited>")
                        && bomFlatten.substring(0, bomFlatten.indexOf("</plugin>")).contains("<inherited>false</inherited>"),
                "chaos-boot-parent 与 chaos-dependencies 的 flatten 插件必须 inherited=false，避免进入业务应用构建");
    }

    /**
     * 依赖版本只允许在 chaos-dependencies 维护一份。
     *
     * <p>chaos-dependencies 不继承任何 parent，保证发布出去的 BOM 不夹带构建配置；
     * 根 POM 以 BOM 为 parent，自身不再声明 dependencyManagement，避免两份清单再次漂移。</p>
     */
    @Test
    void dependencyVersionsShouldHaveSingleSourceOfTruth() throws IOException {
        Path root = projectRoot();
        String bom = Files.readString(root.resolve("chaos-dependencies/pom.xml"), StandardCharsets.UTF_8);
        String parent = Files.readString(root.resolve("pom.xml"), StandardCharsets.UTF_8);

        assertTrue(!bom.contains("<parent>"), "chaos-dependencies 必须是独立 BOM，不得继承 chaos-parent");
        assertTrue(
                parent.contains("<artifactId>chaos-dependencies</artifactId>") && !parent.contains("<dependencyManagement>"),
                "根 POM 必须以 chaos-dependencies 为 parent，且不得重复声明 dependencyManagement"
        );

        List<String> hardcodedVersions = new ArrayList<>();
        for (Path pom : pomFiles(root)) {
            if (pom.equals(root.resolve("chaos-dependencies/pom.xml"))) {
                continue;
            }
            String source = Files.readString(pom, StandardCharsets.UTF_8);
            if (source.contains("<version>${project.version}</version>")) {
                hardcodedVersions.add(root.relativize(pom) + " -> 框架模块版本应由 BOM 管理");
            }
        }
        assertTrue(hardcodedVersions.isEmpty(), () -> "模块 POM 出现冗余版本声明:\n" + String.join("\n", hardcodedVersions));
    }

    /**
     * 发布治理文档必须包含版本矩阵、starter 依赖树、API 兼容和 changelog 要求。
     */
    @Test
    void releaseGovernanceDocumentationShouldBeComplete() throws IOException {
        Path root = projectRoot();
        Path releaseDoc = root.resolve("chaos-docs/src/main/resources/docs/release-governance.md");
        Path changelog = root.resolve("CHANGELOG.md");
        String doc = Files.readString(releaseDoc, StandardCharsets.UTF_8);

        assertTrue(Files.exists(changelog), "仓库根目录必须提供 CHANGELOG.md");
        assertTrue(
                doc.contains("版本兼容矩阵")
                        && doc.contains("API 兼容检查")
                        && doc.contains("Starter 依赖树")
                        && doc.contains("Changelog 规范")
                        && doc.contains("mvn -Pchaos-release"),
                "发布治理文档必须覆盖兼容矩阵、API 兼容、starter 依赖树、changelog 和发布命令"
        );
        assertTrue(
                Files.readString(root.resolve("chaos-docs/src/main/resources/docs/index.md"), StandardCharsets.UTF_8)
                        .contains("release-governance.md"),
                "文档索引必须链接发布治理文档"
        );
    }

    /**
     * 授权服务器必须阻止生产环境携带开发默认值上线。
     */
    @Test
    void authorizationServerShouldGuardProductionDefaults() throws IOException {
        Path checker = projectRoot().resolve(
                "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/core/AuthorizationProductionSafetyChecker.java"
        );
        Path properties = projectRoot().resolve(
                "chaos-security/chaos-authorization/src/main/java/com/michael/chaos/authorization/core/ChaosAuthorizationProperties.java"
        );
        Path autoConfiguration = projectRoot().resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/authorization/ChaosAuthorizationAutoConfiguration.java"
        );

        assertTrue(Files.exists(checker), "授权服务器必须提供生产安全检查器");
        assertTrue(
                Files.readString(checker, StandardCharsets.UTF_8).contains("ApplicationRunner")
                        && Files.readString(checker, StandardCharsets.UTF_8).contains("localhost")
                        && Files.readString(checker, StandardCharsets.UTF_8).contains("{noop}")
                        && Files.readString(checker, StandardCharsets.UTF_8).contains("MEMORY")
                        && Files.readString(checker, StandardCharsets.UTF_8).contains("usesGeneratedJwk")
                        && Files.readString(checker, StandardCharsets.UTF_8).contains("InMemoryOAuth2AuthorizationService")
                        && Files.readString(checker, StandardCharsets.UTF_8).contains("InMemoryOAuth2AuthorizationConsentService")
                        && Files.readString(checker, StandardCharsets.UTF_8).contains("InMemoryAuthorizationSessionRegistry")
                        && Files.readString(checker, StandardCharsets.UTF_8).contains("NoopJwtRevocationService")
                        && Files.readString(checker, StandardCharsets.UTF_8).contains("NoopAuthorizationKickoutService")
                        && Files.readString(checker, StandardCharsets.UTF_8).contains("NoopAuditEventPublisher"),
                "生产安全检查器必须检查 localhost issuer、noop secret、memory client、临时 JWK、内存授权仓储、Noop JWT 撤销、Noop 互踢和空审计"
        );
        assertTrue(
                Files.readString(properties, StandardCharsets.UTF_8).contains("ProductionSafety")
                        && Files.readString(properties, StandardCharsets.UTF_8).contains("allowMemoryAuthorizationStore")
                        && Files.readString(properties, StandardCharsets.UTF_8).contains("allowMemorySessionRegistry")
                        && Files.readString(properties, StandardCharsets.UTF_8).contains("allowNoopJwtRevocationService")
                        && Files.readString(properties, StandardCharsets.UTF_8).contains("allowNoopKickoutService")
                        && Files.readString(properties, StandardCharsets.UTF_8).contains("allowNoopAuditPublisher"),
                "授权配置必须暴露 production-safety 配置项"
        );
        assertTrue(
                Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("AuthorizationProductionSafetyChecker")
                        && Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("ObjectProvider<RegisteredClientRepository>")
                        && Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("ObjectProvider<OAuth2AuthorizationService>")
                        && Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("ObjectProvider<AuditEventPublisher>"),
                "授权自动装配必须注册生产安全检查器，并传入实际 Bean Provider"
        );
    }

    /**
     * 对象存储自动装配必须显式选择 provider，避免同时存在多套 SDK 时靠顺序隐式生效。
     */
    @Test
    void storageAutoConfigurationShouldRequireExplicitProvider() throws IOException {
        Path properties = projectRoot().resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/storage/ChaosStorageProperties.java"
        );
        Path autoConfiguration = projectRoot().resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/storage/ChaosStorageAutoConfiguration.java"
        );
        String autoConfigurationSource = Files.readString(autoConfiguration, StandardCharsets.UTF_8);

        assertTrue(
                Files.readString(properties, StandardCharsets.UTF_8).contains("Provider")
                        && Files.readString(properties, StandardCharsets.UTF_8).contains("NONE")
                        && Files.readString(properties, StandardCharsets.UTF_8).contains("OSS")
                        && Files.readString(properties, StandardCharsets.UTF_8).contains("MINIO"),
                "对象存储必须提供 chaos.storage.provider 显式选择配置"
        );
        assertTrue(
                autoConfigurationSource.contains("prefix = \"chaos.storage\", name = \"provider\", havingValue = \"oss\"")
                        && autoConfigurationSource.contains("prefix = \"chaos.storage\", name = \"provider\", havingValue = \"minio\"")
                        && !autoConfigurationSource.contains("prefix = \"chaos.storage.oss\", name = \"endpoint\"")
                        && !autoConfigurationSource.contains("prefix = \"chaos.storage.minio\", name = \"endpoint\""),
                "对象存储自动装配必须按 chaos.storage.provider 启用，不应按 endpoint 隐式启用"
        );
    }

    /**
     * 生产模式必须检测危险 Noop/InMemory 兜底实现；默认 fail-fast 阻断启动，可通过
     * {@code chaos.production-safety.fail-fast=false} 降级为告警。
     */
    @Test
    void productionSafetyShouldWarnAboutUnsafeNoopAndInMemoryDefaults() throws IOException {
        Path root = projectRoot();
        List<Path> autoConfigurations = List.of(
                root.resolve("chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/security/ChaosSecurityAutoConfiguration.java"),
                root.resolve("chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/tenant/ChaosTenantAutoConfiguration.java"),
                root.resolve("chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/web/ChaosWebAutoConfiguration.java"),
                root.resolve("chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/gateway/ChaosGatewayAutoConfiguration.java"),
                root.resolve("chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/mybatis/ChaosMybatisAutoConfiguration.java")
        );
        List<String> requiredUnsafeTypes = List.of(
                "NoopJwtRevocationService",
                "NoopTenantStatusProvider",
                "InMemoryRateLimiter",
                "InMemoryIdempotentRepository",
                "NoopDataScopeProvider"
        );
        String combinedSources = "";
        for (Path autoConfiguration : autoConfigurations) {
            String source = Files.readString(autoConfiguration, StandardCharsets.UTF_8);
            combinedSources += source;
            assertTrue(
                    (source.contains("ProductionSafety.warnUnsafeDefaultBeans")
                            || source.contains("ProductionSafetyEnforcer"))
                            && source.contains("SmartInitializingSingleton"),
                    projectRoot().relativize(autoConfiguration)
                            + " 必须注册生产安全检查器，拦截危险默认实现"
            );
        }
        for (String requiredUnsafeType : requiredUnsafeTypes) {
            assertTrue(
                    combinedSources.contains(requiredUnsafeType),
                    "生产安全检查必须覆盖 " + requiredUnsafeType
            );
        }
        assertTrue(
                Files.readString(root.resolve("chaos-docs/src/main/resources/docs/configuration-index.md"), StandardCharsets.UTF_8)
                        .contains("chaos.production-safety"),
                "配置索引必须记录 chaos.production-safety"
        );
    }

    /**
     * Trace 上下文必须支持线程池和异步任务显式传播，避免依赖 InheritableThreadLocal。
     */
    @Test
    void traceContextShouldProvideAsyncPropagationBoundary() throws IOException {
        Path traceContext = projectRoot().resolve(
                "chaos-observability/chaos-trace/src/main/java/com/michael/chaos/trace/TraceContext.java"
        );
        Path requestContext = projectRoot().resolve(
                "chaos-foundation/chaos-core/src/main/java/com/michael/chaos/core/context/RequestContext.java"
        );
        Path tenantContext = projectRoot().resolve(
                "chaos-tenant/src/main/java/com/michael/chaos/tenant/TenantContext.java"
        );
        Path snapshot = projectRoot().resolve(
                "chaos-observability/chaos-trace/src/main/java/com/michael/chaos/trace/TraceContextSnapshot.java"
        );
        Path taskDecorator = projectRoot().resolve(
                "chaos-service/src/main/java/com/michael/chaos/service/context/TraceContextTaskDecorator.java"
        );
        Path autoConfiguration = projectRoot().resolve(
                "chaos-autoconfigure/src/main/java/com/michael/chaos/autoconfigure/service/ChaosServiceAutoConfiguration.java"
        );

        assertTrue(Files.exists(snapshot), "Trace 模块必须提供上下文快照");
        assertTrue(
                Files.readString(traceContext, StandardCharsets.UTF_8).contains("TraceContextSnapshot capture()")
                        && Files.readString(traceContext, StandardCharsets.UTF_8).contains("Scope implements AutoCloseable")
                        && Files.readString(traceContext, StandardCharsets.UTF_8).contains("Runnable wrap"),
                "TraceContext 必须提供 capture、Scope 和 Runnable wrap API"
        );
        assertTrue(
                Files.readString(taskDecorator, StandardCharsets.UTF_8).contains("implements TaskDecorator")
                        && Files.readString(taskDecorator, StandardCharsets.UTF_8).contains("ContextPropagation.wrap"),
                "Service 模块必须提供 TaskDecorator 传播 trace 上下文"
        );
        assertTrue(
                Files.readString(autoConfiguration, StandardCharsets.UTF_8).contains("TraceContextTaskDecorator"),
                "Service 自动装配必须注册 trace 上下文 TaskDecorator"
        );
        assertTrue(
                !Files.readString(requestContext, StandardCharsets.UTF_8).contains("InheritableThreadLocal")
                        && !Files.readString(traceContext, StandardCharsets.UTF_8).contains("InheritableThreadLocal")
                        && !Files.readString(tenantContext, StandardCharsets.UTF_8).contains("InheritableThreadLocal"),
                "请求、trace 和租户上下文不得依赖 InheritableThreadLocal，必须通过显式快照传播"
        );
    }

    /**
     * 工程必须提供 CI 质量门禁和示例链路 smoke 入口。
     */
    @Test
    void projectShouldProvideCiAndExampleSmokeEntryPoints() throws IOException {
        Path root = projectRoot();
        Path workflow = root.resolve(".github/workflows/ci.yml");
        Path structureScript = root.resolve("scripts/verify-structure.sh");
        Path smokeScript = root.resolve("scripts/smoke-examples-jwt.sh");
        Path doc = root.resolve("chaos-docs/src/main/resources/docs/ci-and-smoke.md");

        assertTrue(Files.exists(workflow), "工程必须提供 GitHub Actions CI workflow");
        assertTrue(
                Files.readString(workflow, StandardCharsets.UTF_8).contains("mvnw -B")
                        && Files.readString(workflow, StandardCharsets.UTF_8).contains("chaos-release")
                        && Files.readString(workflow, StandardCharsets.UTF_8).contains("scripts/verify-structure.sh"),
                "CI 必须执行单测、发布治理 validate 和结构扫描"
        );
        assertTrue(Files.exists(structureScript), "工程必须提供结构扫描脚本");
        assertTrue(
                Files.readString(structureScript, StandardCharsets.UTF_8).contains("package-info.java")
                        && Files.readString(structureScript, StandardCharsets.UTF_8).contains("spring.factories")
                        && Files.readString(structureScript, StandardCharsets.UTF_8).contains("*-starter"),
                "结构扫描脚本必须覆盖 package-info、spring.factories 和 starter Java 代码"
        );
        assertTrue(Files.exists(smokeScript), "工程必须提供示例 JWT 链路 smoke 脚本");
        assertTrue(
                Files.readString(smokeScript, StandardCharsets.UTF_8).contains("example-auth-server")
                        && Files.readString(smokeScript, StandardCharsets.UTF_8).contains("example-order-service")
                        && Files.readString(smokeScript, StandardCharsets.UTF_8).contains("example-gateway")
                        && Files.readString(smokeScript, StandardCharsets.UTF_8).contains("/oauth2/token")
                        && Files.readString(smokeScript, StandardCharsets.UTF_8).contains("/actuator/prometheus"),
                "smoke 脚本必须覆盖三服务、登录和 Prometheus"
        );
        assertTrue(Files.exists(doc), "工程必须提供 CI 与 smoke 文档");
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
        }
    }

    private static List<Path> pomFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> Objects.equals(path.getFileName().toString(), "pom.xml"))
                    .filter(path -> !path.toString().contains("/target/"))
                    // archetype 模板中的 pom.xml 属于生成项目，不参与仓库模块规则。
                    .filter(path -> !path.toString().contains("/archetype-resources/"))
                    .toList();
        }
    }

    private static NestedField nestedField(String fieldLine) {
        if (!fieldLine.startsWith("private ") || !fieldLine.endsWith(";")) {
            return null;
        }
        String[] tokens = fieldLine
                .replace(";", "")
                .replace("=", " = ")
                .trim()
                .split("\\s+");
        if (tokens.length < 3 || !Objects.equals(tokens[0], "private")) {
            return null;
        }
        String type = tokens[1];
        String name = tokens[2];
        if (type.contains("<") || name.contains("=")) {
            return null;
        }
        return new NestedField(type, name);
    }

    private record NestedField(String type, String name) {
    }

    private static boolean isExcludedFromPublicBom(Path root, Path pom) {
        String relativePath = root.relativize(pom).toString();
        return Objects.equals(relativePath, "pom.xml")
                || Objects.equals(relativePath, "chaos-starters/pom.xml")
                || Objects.equals(relativePath, "chaos-docs/pom.xml")
                || Objects.equals(relativePath, "chaos-architecture-tests/pom.xml")
                || relativePath.startsWith("chaos-examples/")
                // archetype 是项目生成器而不是运行时依赖，使用方通过 archetype:generate 直接引用坐标。
                || relativePath.startsWith("chaos-archetypes/");
    }

    /**
     * 计算需要进入公共 BOM 的 artifactId：排除根/聚合/示例/文档/仓库测试模块以及各 *-parent 聚合 POM。
     */
    private static Set<String> publishedArtifactIds(Path root) throws IOException {
        Set<String> artifacts = new LinkedHashSet<>();
        for (Path pom : pomFiles(root)) {
            if (isExcludedFromPublicBom(root, pom)) {
                continue;
            }
            String artifactId = artifactIdOf(pom);
            if (artifactId.endsWith("-parent") || Objects.equals(artifactId, "chaos-dependencies")) {
                continue;
            }
            artifacts.add(artifactId);
        }
        return artifacts;
    }

    /**
     * 解析 BOM 中 groupId 为 com.michael 的受管 artifactId。
     */
    private static Set<String> chaosArtifactsManagedBy(Path bom) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Element project = factory.newDocumentBuilder().parse(bom.toFile()).getDocumentElement();
            Set<String> artifacts = new LinkedHashSet<>();
            org.w3c.dom.NodeList dependencies = project.getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                Element dependency = (Element) dependencies.item(i);
                String groupId = childText(dependency, "groupId");
                String artifactId = childText(dependency, "artifactId");
                if (Objects.equals(groupId, "com.michael") && artifactId != null) {
                    artifacts.add(artifactId);
                }
            }
            return artifacts;
        } catch (Exception ex) {
            throw new IllegalStateException("BOM 解析失败: " + bom, ex);
        }
    }

    private static String childText(Element element, String tagName) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element childElement && Objects.equals(childElement.getTagName(), tagName)) {
                return childElement.getTextContent().trim();
            }
        }
        return null;
    }

    /**
     * 截取类声明前的 {@code @AutoConfiguration(...)} 注解文本，避免断言依赖注解的具体换行和属性顺序。
     */
    private static String autoConfigurationAnnotation(String source) {
        int start = source.indexOf("@AutoConfiguration");
        if (start < 0) {
            return "";
        }
        int classDeclaration = source.indexOf("public class ", start);
        return classDeclaration < 0 ? source.substring(start) : source.substring(start, classDeclaration);
    }

    private static String artifactIdOf(Path pom) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Element project = factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
            for (Node child = project.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element element && Objects.equals(element.getTagName(), "artifactId")) {
                    return element.getTextContent().trim();
                }
            }
            throw new IllegalStateException("POM 缺少 artifactId: " + pom);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("POM 解析失败: " + pom, ex);
        }
    }

    private static Path projectRoot() {
        String multiModuleRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleRoot != null && !multiModuleRoot.isBlank()) {
            return Path.of(multiModuleRoot).toAbsolutePath().normalize();
        }
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Objects.equals(current.getFileName().toString(), "chaos-architecture-tests")) {
            return current.getParent();
        }
        return current;
    }
}
