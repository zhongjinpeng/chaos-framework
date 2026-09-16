package com.michael.chaos.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.michael.chaos.architecture.ProjectModel.Dependency;
import com.michael.chaos.architecture.ProjectModel.Module;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 模块依赖方向规则。
 *
 * <p>2.0 结构调整把“谁可以依赖谁”固定下来，这里用测试防止回退。规则编号与
 * {@code docs/architecture.md}「依赖方向规则」一节一致：</p>
 * <ol>
 *     <li>chaos-core 不依赖任何 chaos 模块，也不依赖 Spring Web / Servlet / Security；</li>
 *     <li>chaos-security-api 只依赖 chaos-core，不依赖 Spring Security；</li>
 *     <li>chaos-gateway、chaos-gateway-nacos、chaos-mybatis、chaos-redis 只能依赖 chaos-security-api，
 *     不得依赖 chaos-security / chaos-authorization / chaos-security-redis；</li>
 *     <li>chaos-redis 不含任何安全领域概念；</li>
 *     <li>chaos-autoconfigure 只放装配代码，不放 Filter / Interceptor / Repository / Service 等实现
 *     （“{@code @AutoConfiguration} 只能在 chaos-autoconfigure”由 {@code LayerBoundaryArchitectureTest} 校验）；</li>
 *     <li>starter 不得依赖示例工程、测试支持和架构测试模块；</li>
 *     <li>库模块不得依赖 chaos-autoconfigure（由 {@code LayerBoundaryArchitectureTest} 校验）；</li>
 *     <li>chaos-test-support 只能以 test scope 被依赖；</li>
 *     <li>每个模块的 Java 包必须位于该模块的基础包 {@code com.michael.chaos.<module>} 下；</li>
 *     <li>场景 starter 只聚合能力 starter（外加少量可观测性依赖），能力 starter 不得反向依赖场景 starter。</li>
 * </ol>
 */
class DependencyDirectionArchitectureTest {

    private static final Set<String> SECURITY_IMPLEMENTATION_MODULES = Set.of(
            "chaos-security", "chaos-authorization", "chaos-security-redis");

    private static final List<String> SECURITY_API_ONLY_MODULES = List.of(
            "chaos-gateway", "chaos-gateway-nacos", "chaos-mybatis", "chaos-redis");

    /**
     * 场景 starter：使用方按“要搭什么服务”只选一个。网关 starter 历史上同时承担能力聚合，不在此列。
     */
    static final Set<String> SCENARIO_STARTERS = Set.of("chaos-web-service-starter", "chaos-auth-server-starter");

    /**
     * 场景 starter 允许直接声明的非 chaos 依赖：仅限服务运维必需的可观测性端点。
     */
    private static final Set<String> SCENARIO_STARTER_ALLOWED_LIBRARIES = Set.of(
            "org.springframework.boot:spring-boot-starter-actuator",
            "io.micrometer:micrometer-registry-prometheus");

    private static final List<String> WEB_FRAMEWORK_IMPORTS = List.of(
            "org.springframework.web.", "org.springframework.http.", "org.springframework.security.", "jakarta.servlet.");

    /**
     * 自动装配模块中不允许出现的实现类后缀：这些类型应放在对应功能模块，便于脱离自动装配单独使用和测试。
     */
    private static final Pattern IMPLEMENTATION_TYPE = Pattern.compile(
            "(?m)^(?:public\\s+|protected\\s+)?(?:abstract\\s+|final\\s+)*(?:class|record)\\s+"
                    + "(\\w+(?:Filter|Interceptor|Repository|Service|Controller|Aspect|Store|Registry|Publisher|Listener))\\b");

    private static final Pattern IMPLEMENTS_WEB_EXTENSION = Pattern.compile(
            "\\b(?:extends\\s+OncePerRequestFilter|implements\\s+[^{]*\\b(?:Filter|HandlerInterceptor|GlobalFilter|WebFilter)\\b)");

    /**
     * artifactId → 基础包。新增模块时必须在这里登记，未登记会让规则 9 失败。
     */
    private static final Map<String, String> BASE_PACKAGES = Map.ofEntries(
            Map.entry("chaos-core", "com.michael.chaos.core"),
            Map.entry("chaos-domain", "com.michael.chaos.domain"),
            Map.entry("chaos-trace", "com.michael.chaos.trace"),
            Map.entry("chaos-audit", "com.michael.chaos.audit"),
            Map.entry("chaos-audit-jdbc", "com.michael.chaos.audit.jdbc"),
            Map.entry("chaos-tenant", "com.michael.chaos.tenant"),
            Map.entry("chaos-security-api", "com.michael.chaos.security.api"),
            Map.entry("chaos-security", "com.michael.chaos.security"),
            Map.entry("chaos-security-redis", "com.michael.chaos.security.redis"),
            Map.entry("chaos-authorization", "com.michael.chaos.authorization"),
            Map.entry("chaos-web", "com.michael.chaos.web"),
            Map.entry("chaos-service", "com.michael.chaos.service"),
            Map.entry("chaos-mybatis", "com.michael.chaos.mybatis"),
            Map.entry("chaos-redis", "com.michael.chaos.redis"),
            Map.entry("chaos-mq", "com.michael.chaos.mq"),
            Map.entry("chaos-mq-jdbc", "com.michael.chaos.mq.jdbc"),
            Map.entry("chaos-mq-kafka", "com.michael.chaos.mq.kafka"),
            Map.entry("chaos-mq-rocketmq", "com.michael.chaos.mq.rocketmq"),
            Map.entry("chaos-gateway", "com.michael.chaos.gateway"),
            Map.entry("chaos-gateway-nacos", "com.michael.chaos.gateway.nacos"),
            Map.entry("chaos-job", "com.michael.chaos.job"),
            Map.entry("chaos-storage", "com.michael.chaos.storage"),
            Map.entry("chaos-storage-minio", "com.michael.chaos.storage.minio"),
            Map.entry("chaos-storage-oss", "com.michael.chaos.storage.oss"),
            Map.entry("chaos-autoconfigure", "com.michael.chaos.autoconfigure"),
            Map.entry("chaos-test-support", "com.michael.chaos.test"),
            Map.entry("example-auth-server", "com.michael.chaos.examples.auth"),
            Map.entry("example-gateway", "com.michael.chaos.examples.gateway"),
            Map.entry("example-order-service", "com.michael.chaos.examples.order")
    );

    private static ProjectModel project;

    @BeforeAll
    static void loadProject() throws IOException {
        project = ProjectModel.load();
    }

    /**
     * 规则 1：chaos-core 是最底层，不得反向依赖其他 chaos 模块或 Web 框架。
     */
    @Test
    void coreShouldNotDependOnOtherModulesOrWebFrameworks() throws IOException {
        Module core = project.module("chaos-core");
        List<String> violations = new ArrayList<>();
        core.productionChaosDependencies()
                .forEach(dependency -> violations.add("pom.xml 依赖了 " + dependency));
        core.dependencies().stream()
                .filter(dependency -> !dependency.isTest())
                .filter(DependencyDirectionArchitectureTest::isWebOrSecurityFramework)
                .forEach(dependency -> violations.add("pom.xml 依赖了 Web/Security 框架 " + dependency));
        violations.addAll(forbiddenImports(core, WEB_FRAMEWORK_IMPORTS, List.of()));

        assertTrue(violations.isEmpty(), () -> failure(1, "chaos-core 必须保持框架无关", violations));
    }

    /**
     * 规则 2：chaos-security-api 是 gateway / mybatis / redis 能看到的唯一安全契约，必须不依赖 Spring Security。
     */
    @Test
    void securityApiShouldOnlyDependOnCore() throws IOException {
        Module api = project.module("chaos-security-api");
        List<String> violations = new ArrayList<>();
        api.productionChaosDependencies().stream()
                .filter(dependency -> !dependency.artifactId().equals("chaos-core"))
                .forEach(dependency -> violations.add("pom.xml 依赖了 " + dependency));
        api.dependencies().stream()
                .filter(dependency -> !dependency.isTest())
                .filter(dependency -> dependency.groupId().startsWith("org.springframework"))
                .forEach(dependency -> violations.add("pom.xml 依赖了 Spring " + dependency));
        violations.addAll(forbiddenImports(api, List.of("org.springframework."), List.of()));

        assertTrue(violations.isEmpty(), () -> failure(2, "chaos-security-api 只能依赖 chaos-core", violations));
    }

    /**
     * 规则 3：基础设施适配模块只能看到安全契约，不能把 Servlet 过滤器、AOP、授权服务器带进自己的 classpath。
     */
    @Test
    void infrastructureModulesShouldOnlyDependOnSecurityApi() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String artifactId : SECURITY_API_ONLY_MODULES) {
            Module module = project.module(artifactId);
            module.productionChaosDependencies().stream()
                    .filter(dependency -> SECURITY_IMPLEMENTATION_MODULES.contains(dependency.artifactId()))
                    .forEach(dependency -> violations.add(artifactId + " pom.xml 依赖了 " + dependency));
            violations.addAll(forbiddenImports(
                    module,
                    List.of("com.michael.chaos.security.", "com.michael.chaos.authorization."),
                    List.of("com.michael.chaos.security.api.")));
        }

        assertTrue(violations.isEmpty(),
                () -> failure(3, "gateway / mybatis / redis 只能依赖 chaos-security-api", violations));
    }

    /**
     * 规则 4：chaos-redis 是通用 Redis 能力，Redis 版安全实现只能放在 chaos-security-redis。
     */
    @Test
    void redisShouldNotContainSecurityConcepts() throws IOException {
        Module redis = project.module("chaos-redis");
        List<String> violations = new ArrayList<>();
        redis.productionChaosDependencies().stream()
                .filter(dependency -> dependency.artifactId().startsWith("chaos-security")
                        || dependency.artifactId().equals("chaos-authorization"))
                .forEach(dependency -> violations.add("pom.xml 依赖了 " + dependency));
        violations.addAll(forbiddenImports(
                redis,
                List.of("com.michael.chaos.security.", "com.michael.chaos.authorization.", "org.springframework.security."),
                List.of()));
        Pattern securityTypeName = Pattern.compile("(Jwt|Token|Authorization|Login|Captcha|Kickout|Oauth|OAuth2)");
        for (Path source : ProjectModel.mainSources(redis)) {
            String fileName = source.getFileName().toString();
            if (securityTypeName.matcher(fileName).find()) {
                violations.add(project.relative(source) + " 类名包含安全领域概念，应迁到 chaos-security-redis");
            }
        }

        assertTrue(violations.isEmpty(), () -> failure(4, "chaos-redis 不得包含安全领域实现", violations));
    }

    /**
     * 规则 5：chaos-autoconfigure 只负责装配；Filter、Interceptor、Repository 等实现放回功能模块。
     */
    @Test
    void autoconfigureShouldOnlyContainWiring() throws IOException {
        Module autoconfigure = project.module("chaos-autoconfigure");
        List<String> violations = new ArrayList<>();
        for (Path source : ProjectModel.mainSources(autoconfigure)) {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            Matcher typeMatcher = IMPLEMENTATION_TYPE.matcher(content);
            if (typeMatcher.find()) {
                violations.add(project.relative(source) + " 声明了实现类型 " + typeMatcher.group(1));
            }
            if (IMPLEMENTS_WEB_EXTENSION.matcher(content).find()) {
                violations.add(project.relative(source) + " 实现了 Filter / HandlerInterceptor / GlobalFilter / WebFilter");
            }
        }

        assertTrue(violations.isEmpty(), () -> failure(5, "chaos-autoconfigure 只能包含装配代码", violations));
    }

    /**
     * 规则 6：starter 是面向使用方的依赖入口，不能把示例、测试支持或架构测试带进业务应用。
     */
    @Test
    void startersShouldNotDependOnExamplesOrTestModules() {
        List<String> violations = new ArrayList<>();
        for (Module module : project.modules().values()) {
            if (!module.isStarter()) {
                continue;
            }
            module.dependencies().stream()
                    .filter(Dependency::isChaos)
                    .filter(dependency -> dependency.artifactId().startsWith("example-")
                            || dependency.artifactId().equals("chaos-test-support")
                            || dependency.artifactId().equals("chaos-architecture-tests"))
                    .forEach(dependency -> violations.add(module.artifactId() + " 依赖了 " + dependency));
        }

        assertTrue(violations.isEmpty(), () -> failure(6, "starter 不得依赖示例或测试模块", violations));
    }

    /**
     * 规则 8：chaos-test-support 只服务测试，不得以 compile / runtime 形式泄漏到发布产物。
     */
    @Test
    void testSupportShouldOnlyBeUsedInTestScope() {
        List<String> violations = new ArrayList<>();
        for (Module module : project.modules().values()) {
            module.dependencies().stream()
                    .filter(dependency -> dependency.isChaos() && dependency.artifactId().equals("chaos-test-support"))
                    .filter(dependency -> !dependency.isTest())
                    .forEach(dependency -> violations.add(module.artifactId() + " 以非 test scope 依赖了 " + dependency));
        }

        assertTrue(violations.isEmpty(), () -> failure(8, "chaos-test-support 只能以 test scope 引入", violations));
    }

    /**
     * 规则 9：Java 包必须位于所属模块的基础包下，且不能落入其他模块更具体的基础包。
     *
     * <p>包与模块一一对应，使用方看到 FQCN 就能知道该引入哪个 artifact，也避免跨 jar 的拆分包。</p>
     */
    @Test
    void packagesShouldMatchModuleBasePackage() throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> basesBySpecificity = BASE_PACKAGES.values().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (Module module : project.modules().values()) {
            List<Path> sources = ProjectModel.mainSources(module);
            if (sources.isEmpty()) {
                continue;
            }
            String expected = BASE_PACKAGES.get(module.artifactId());
            if (expected == null) {
                violations.add(module.artifactId() + "（" + module.path() + "）未在 BASE_PACKAGES 中登记基础包");
                continue;
            }
            for (Path source : sources) {
                Optional<String> declared = ProjectModel.packageOf(source);
                if (declared.isEmpty()) {
                    violations.add(project.relative(source) + " 缺少 package 声明");
                    continue;
                }
                String owner = basesBySpecificity.stream()
                        .filter(base -> declared.get().equals(base) || declared.get().startsWith(base + "."))
                        .findFirst()
                        .orElse("");
                if (!owner.equals(expected)) {
                    violations.add(project.relative(source) + " 包 " + declared.get()
                            + " 不属于 " + module.artifactId() + " 的基础包 " + expected);
                }
            }
        }
        List<Path> architectureSources = ProjectModel.javaFiles(
                project.module("chaos-architecture-tests").directory().resolve("src/test/java"));
        for (Path source : architectureSources) {
            String declared = ProjectModel.packageOf(source).orElse("");
            if (!declared.equals("com.michael.chaos.architecture") && !declared.startsWith("com.michael.chaos.architecture.")) {
                violations.add(project.relative(source) + " 包 " + declared + " 应位于 com.michael.chaos.architecture");
            }
        }

        assertTrue(violations.isEmpty(), () -> failure(9, "Java 包必须与模块基础包一致", violations));
    }

    /**
     * 规则 10：场景 starter 只是能力 starter 的组合。
     *
     * <p>每项能力的依赖清单只维护在对应能力 starter 中；场景 starter 直接依赖库模块或三方框架，
     * 会让同一能力在两处维护依赖并逐渐漂移。能力 starter 反向依赖场景 starter 则会把整套服务能力强加给只想要单项能力的应用。</p>
     */
    @Test
    void scenarioStartersShouldOnlyAggregateCapabilityStarters() {
        List<String> violations = new ArrayList<>();
        for (String scenario : SCENARIO_STARTERS) {
            Module module = project.module(scenario);
            if (module.dependencies().isEmpty()) {
                violations.add(scenario + " 未聚合任何能力 starter");
            }
            for (Dependency dependency : module.dependencies()) {
                String coordinate = dependency.groupId() + ":" + dependency.artifactId();
                boolean capabilityStarter = dependency.isChaos()
                        && dependency.artifactId().endsWith("-starter")
                        && !SCENARIO_STARTERS.contains(dependency.artifactId());
                if (!capabilityStarter && !SCENARIO_STARTER_ALLOWED_LIBRARIES.contains(coordinate)) {
                    violations.add(scenario + " 直接依赖了 " + dependency + "，应改为依赖对应能力 starter");
                }
                if (dependency.optional() || !"compile".equals(dependency.scope())) {
                    violations.add(scenario + " 的依赖 " + dependency + " 必须是 compile 且非 optional，否则无法传递给使用方");
                }
            }
        }
        for (Module module : project.modules().values()) {
            if (!module.isStarter() || SCENARIO_STARTERS.contains(module.artifactId())) {
                continue;
            }
            module.dependencies().stream()
                    .filter(dependency -> dependency.isChaos() && SCENARIO_STARTERS.contains(dependency.artifactId()))
                    .forEach(dependency -> violations.add(module.artifactId() + " 是能力 starter，不得依赖场景 starter " + dependency));
        }

        assertTrue(violations.isEmpty(), () -> failure(10, "场景 starter 只能聚合能力 starter", violations));
    }

    private static boolean isWebOrSecurityFramework(Dependency dependency) {
        return dependency.artifactId().startsWith("spring-web")
                || dependency.artifactId().startsWith("spring-security")
                || dependency.artifactId().startsWith("spring-boot-starter-web")
                || dependency.artifactId().startsWith("spring-boot-starter-security")
                || dependency.groupId().equals("jakarta.servlet");
    }

    private static List<String> forbiddenImports(Module module, List<String> forbiddenPrefixes, List<String> allowedPrefixes)
            throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : ProjectModel.mainSources(module)) {
            for (String imported : ProjectModel.imports(source)) {
                boolean forbidden = forbiddenPrefixes.stream().anyMatch(imported::startsWith);
                boolean allowed = allowedPrefixes.stream().anyMatch(imported::startsWith);
                if (forbidden && !allowed) {
                    violations.add(project.relative(source) + " import " + imported);
                }
            }
        }
        return violations;
    }

    private static String failure(int rule, String title, List<String> violations) {
        return "依赖方向规则 " + rule + " 被破坏：" + title + "（见 docs/architecture.md「依赖方向规则」）\n  "
                + String.join("\n  ", violations);
    }
}
