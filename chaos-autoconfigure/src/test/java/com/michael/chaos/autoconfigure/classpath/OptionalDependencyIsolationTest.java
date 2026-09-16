package com.michael.chaos.autoconfigure.classpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 可选依赖缺失时的真实类加载测试。
 *
 * <p>chaos-autoconfigure 把所有功能库与三方框架声明为 optional，业务方只引入部分 starter。
 * 如果某个自动装配在顶层 {@code @Bean} 方法签名、字段或父类中直接引用了可选类型，而没有用
 * {@code @ConditionalOnClass} 保护（或没有放进受保护的嵌套配置类），应用在缺少该依赖时会以
 * {@code NoClassDefFoundError} 启动失败。</p>
 *
 * <p>本测试为每个场景构造一个以平台类加载器为父的 {@link URLClassLoader}，从测试类路径中物理移除一组 jar
 * （以及以非 optional compile 依赖引用它们的 chaos 模块），然后在该加载器内按真实应用的方式
 * （类名导入 + ASM 条件过滤）导入全部 chaos 自动装配与 Spring Boot 基础自动装配并刷新上下文。</p>
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class OptionalDependencyIsolationTest {

    /**
     * chaos 功能库场景的下限。低于这个数说明类路径解析出了问题，而不是模块真的变少了。
     */
    private static final int MIN_CHAOS_LIBRARY_SCENARIOS = 20;

    private static final String CHAOS_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private static final String PROBE_IMPORTS =
            "META-INF/spring/" + ImportChaosAutoConfigurations.class.getName() + ".imports";

    /**
     * 与 chaos 自动装配协作的 Spring Boot 基础自动装配（例如授权服务器依赖 Boot 提供的 {@code HttpSecurity}）。
     * 只挑选不会在启动时连接外部中间件的项；类文件不在隔离类路径中的项会被跳过。
     */
    private static final List<String> BOOT_AUTO_CONFIGURATIONS = List.of(
            "org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration",
            "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
            "org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration",
            "org.springframework.boot.autoconfigure.aop.AopAutoConfiguration",
            "org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration",
            "org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration",
            "org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration",
            "org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration",
            "org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration",
            "org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration",
            "org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration",
            "org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration"
    );

    private static final String[] PROPERTIES = {
            "chaos.gateway.jwt.jwk-set-uri=http://localhost:9000/oauth2/jwks",
            // Servlet 资源服务器需要 JwtDecoder；Boot 在配置 jwk-set-uri 后创建（不会在启动时访问该地址）。
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9000/oauth2/jwks",
            "spring.datasource.url=jdbc:h2:mem:chaos-isolation;DB_CLOSE_DELAY=-1",
            // 不在上下文刷新时启动后台调度线程，避免派发器访问尚不存在的 outbox 表。
            "chaos.mq.outbox.dispatcher.enabled=false",
            "chaos.mq.outbox.cleanup.enabled=false",
            // 测试类路径同时包含 chaos-web 与 chaos-gateway，servlet 场景下运行栈检查会按设计阻断启动；
            // 本测试关注类加载隔离，运行栈检查由 ChaosDiagnosticsAutoConfigurationTest 覆盖。
            "chaos.diagnostics.web-stack-check.enabled=false",
    };

    /**
     * 三方依赖分组：artifactId 正则。一组代表“使用方没有引入对应 starter”时整体缺失的一族 jar。
     */
    private static final Map<String, String> THIRD_PARTY_GROUPS = new LinkedHashMap<>();

    static {
        THIRD_PARTY_GROUPS.put("redisson", "redisson.*");
        THIRD_PARTY_GROUPS.put("redis", "redisson.*|spring-data-redis|lettuce-core");
        THIRD_PARTY_GROUPS.put("mybatis", "mybatis.*|jsqlparser");
        THIRD_PARTY_GROUPS.put("jdbc", "spring-jdbc|mybatis.*|jsqlparser|HikariCP|h2");
        THIRD_PARTY_GROUPS.put("kafka", "spring-kafka|kafka-clients");
        THIRD_PARTY_GROUPS.put("rocketmq", "rocketmq.*");
        THIRD_PARTY_GROUPS.put("spring-security", "spring-security-.*|nimbus-.*|oauth2-oidc-sdk");
        THIRD_PARTY_GROUPS.put("spring-cloud-gateway", "spring-cloud-gateway.*|spring-cloud-starter-gateway.*");
        THIRD_PARTY_GROUPS.put("nacos", "nacos-.*|spring-alibaba-nacos.*|spring-cloud-starter-alibaba.*|spring-cloud-alibaba-commons");
        THIRD_PARTY_GROUPS.put("openfeign", "feign-.*|spring-cloud-openfeign.*|spring-cloud-starter-openfeign");
        THIRD_PARTY_GROUPS.put("minio", "minio.*");
        THIRD_PARTY_GROUPS.put("aliyun-oss", "aliyun-sdk-oss");
        THIRD_PARTY_GROUPS.put("spring-retry", "spring-retry");
        THIRD_PARTY_GROUPS.put("actuator", "spring-boot-actuator.*");
        THIRD_PARTY_GROUPS.put("micrometer-tracing", "micrometer-tracing.*");
        THIRD_PARTY_GROUPS.put("webmvc", "spring-webmvc|tomcat-embed-.*|springdoc-openapi-starter-webmvc.*");
        THIRD_PARTY_GROUPS.put("webflux", "spring-webflux|reactor-netty.*|spring-cloud-gateway.*|spring-cloud-starter-gateway.*");
    }

    private static final List<ClasspathEntry> CLASSPATH = readClasspath();

    private static final Map<String, Set<String>> CHAOS_COMPILE_DEPENDENCIES = readChaosCompileDependencies();

    @TempDir
    Path probeResources;

    /**
     * 所有可选依赖齐全时，三种应用类型下都能启动（基线，保证其余场景的失败确实来自依赖缺失）。
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("webTypes")
    void shouldStartWithEveryOptionalDependencyPresent(String webType) throws Exception {
        assertStarts(Set.of(), webType);
    }

    /**
     * 使用方没有引入任何功能 starter（只有 chaos-autoconfigure 与 Spring Boot）时也能启动。
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("webTypes")
    void shouldStartWithoutAnyFeatureLibrary(String webType) throws Exception {
        Set<String> excluded = new HashSet<>();
        CLASSPATH.stream().filter(ClasspathEntry::isChaosLibrary).forEach(entry -> excluded.add(entry.artifactId()));
        THIRD_PARTY_GROUPS.values().forEach(regex -> excluded.addAll(matching(regex)));
        if ("servlet".equals(webType)) {
            excluded.removeAll(matching("spring-webmvc|tomcat-embed-.*"));
        } else if ("reactive".equals(webType)) {
            excluded.removeAll(matching("spring-webflux|reactor-netty.*"));
        }
        assertStarts(excluded, webType);
    }

    /**
     * 逐个移除三方依赖族。
     */
    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("thirdPartyScenarios")
    void shouldStartWhenThirdPartyGroupIsAbsent(String group, String webType) throws Exception {
        Set<String> excluded = matching(THIRD_PARTY_GROUPS.get(group));
        assertThat(excluded).as("group %s should match classpath entries", group).isNotEmpty();
        assertStarts(excluded, webType);
    }

    /**
     * 逐个移除 chaos 功能库（按其所属运行栈选择应用类型，覆盖 servlet 与 reactive 两侧）。
     */
    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("chaosLibraryScenarios")
    void shouldStartWhenChaosLibraryIsAbsent(String artifactId, String webType) throws Exception {
        assertStarts(Set.of(artifactId), webType);
    }

    /**
     * 场景枚举自检。
     *
     * <p>{@code @ParameterizedTest} 的参数源产出 0 条时，JUnit 抛 PreconditionViolation，
     * 但那只说明"配置错了"，不会告诉你测试覆盖面已经塌了；而本类的 {@code @MethodSource} 依赖类路径布局解析，
     * 换一个构建阶段（{@code test} vs {@code verify}）就可能整批消失。这里把"场景数量"本身变成断言，
     * 让覆盖面塌方以一条可读的失败信息暴露出来。</p>
     */
    @Test
    void scenarioSourcesShouldNotSilentlyDegrade() {
        assertThat(CLASSPATH).as("test classpath entries").isNotEmpty();
        assertThat(thirdPartyScenarios().count())
                .as("third-party scenarios")
                .isEqualTo(THIRD_PARTY_GROUPS.size() * 2L);
        assertThat(CLASSPATH.stream().filter(ClasspathEntry::isChaosLibrary).map(ClasspathEntry::artifactId).distinct())
                .as("chaos feature libraries resolved from the test classpath")
                .hasSizeGreaterThanOrEqualTo(MIN_CHAOS_LIBRARY_SCENARIOS);
    }

    static Stream<String> webTypes() {
        return Stream.of("none", "servlet", "reactive");
    }

    static Stream<Arguments> thirdPartyScenarios() {
        return THIRD_PARTY_GROUPS.keySet().stream()
                .flatMap(group -> switch (group) {
                    case "webmvc" -> Stream.of(Arguments.of(group, "none"), Arguments.of(group, "reactive"));
                    case "webflux", "spring-cloud-gateway" -> Stream.of(Arguments.of(group, "none"), Arguments.of(group, "servlet"));
                    default -> Stream.of(Arguments.of(group, "servlet"), Arguments.of(group, "reactive"));
                });
    }

    static Stream<Arguments> chaosLibraryScenarios() {
        return CLASSPATH.stream()
                .filter(ClasspathEntry::isChaosLibrary)
                .map(ClasspathEntry::artifactId)
                .distinct()
                .sorted()
                .flatMap(id -> Stream.of(Arguments.of(id, "servlet"), Arguments.of(id, "reactive")));
    }

    private void assertStarts(Set<String> directlyExcluded, String webType) throws Exception {
        Set<String> excluded = withDependents(directlyExcluded);
        List<URL> urls = new ArrayList<>();
        urls.add(probeResources.toUri().toURL());
        for (ClasspathEntry entry : CLASSPATH) {
            if (entry.artifactId() == null || !excluded.contains(entry.artifactId())) {
                urls.add(entry.path().toUri().toURL());
            }
        }
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            writeProbeImports(loader);
            Class<?> probe = Class.forName(ClasspathIsolationProbe.class.getName(), true, loader);
            Method run = probe.getMethod("run", String.class, String[].class);
            String failure = (String) run.invoke(null, webType, PROPERTIES);
            assertThat(failure)
                    .as("excluded=%s, webType=%s", excluded.stream().sorted().toList(), webType)
                    .isNull();
        }
    }

    /**
     * 生成探针导入清单：chaos 主 imports 全部候选 + 隔离类路径中存在的 Boot 基础自动装配。
     * URLClassLoader 对目录条目按需查找资源，因此可以在加载器创建后、首次读取前写入。
     */
    private void writeProbeImports(ClassLoader loader) throws IOException {
        Set<String> candidates = new LinkedHashSet<>();
        for (String name : BOOT_AUTO_CONFIGURATIONS) {
            if (loader.getResource(name.replace('.', '/') + ".class") != null) {
                candidates.add(name);
            }
        }
        try (InputStream in = OptionalDependencyIsolationTest.class.getClassLoader().getResourceAsStream(CHAOS_IMPORTS)) {
            assertThat(in).as("chaos auto-configuration imports").isNotNull();
            new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(candidates::add);
        }
        Path target = probeResources.resolve(PROBE_IMPORTS);
        Files.createDirectories(target.getParent());
        Files.writeString(target, String.join("\n", candidates) + "\n", StandardCharsets.UTF_8);
    }

    private static Set<String> matching(String regex) {
        Set<String> result = new HashSet<>();
        CLASSPATH.stream()
                .filter(entry -> entry.artifactId() != null && entry.artifactId().matches(regex))
                .forEach(entry -> result.add(entry.artifactId()));
        return result;
    }

    /**
     * 被移除的 jar 若被某个 chaos 模块以非 optional 的 compile 依赖引用，该模块在真实应用中也不可能单独存在，一并移除。
     */
    private static Set<String> withDependents(Set<String> excluded) {
        Set<String> result = new LinkedHashSet<>(excluded);
        Deque<String> queue = new ArrayDeque<>(excluded);
        while (!queue.isEmpty()) {
            String removed = queue.poll();
            CHAOS_COMPILE_DEPENDENCIES.forEach((module, dependencies) -> {
                if (dependencies.contains(removed) && result.add(module)) {
                    queue.add(module);
                }
            });
        }
        return result;
    }

    private static List<ClasspathEntry> readClasspath() {
        List<ClasspathEntry> entries = new ArrayList<>();
        for (String element : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!element.isBlank()) {
                entries.add(ClasspathEntry.of(Paths.get(element)));
            }
        }
        return entries;
    }

    private static Map<String, Set<String>> readChaosCompileDependencies() {
        Map<String, Set<String>> result = new HashMap<>();
        for (ClasspathEntry entry : CLASSPATH) {
            if (entry.pom() != null) {
                result.put(entry.artifactId(), compileDependencies(entry.pom()));
            }
        }
        return result;
    }

    private static Set<String> compileDependencies(Path pom) {
        String xml = read(pom).replaceAll("(?s)<parent>.*?</parent>", "")
                .replaceAll("(?s)<dependencyManagement>.*?</dependencyManagement>", "");
        Set<String> result = new HashSet<>();
        Matcher matcher = Pattern.compile("(?s)<dependency>(.*?)</dependency>").matcher(xml);
        while (matcher.find()) {
            String block = matcher.group(1);
            if (block.contains("<optional>true</optional>") || block.matches("(?s).*<scope>(test|provided)</scope>.*")) {
                continue;
            }
            Matcher artifact = Pattern.compile("<artifactId>([^<]+)</artifactId>").matcher(block);
            if (artifact.find()) {
                result.add(artifact.group(1).trim());
            }
        }
        return result;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + path, ex);
        }
    }

    /**
     * 测试类路径条目及其 artifactId（reactor 模块读取相邻 pom.xml，本地仓库 jar 取目录名与同目录 .pom）。
     */
    private record ClasspathEntry(Path path, String artifactId, Path pom) {

        /**
         * 不属于“可选功能库”的 chaos 模块：自动装配模块本身，以及它以 compile 依赖引入的 chaos-core。
         */
        private static final Set<String> NON_LIBRARY_CHAOS = Set.of("chaos-autoconfigure", "chaos-core");

        static ClasspathEntry of(Path path) {
            String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
            if (Files.isDirectory(path) && (fileName.equals("classes") || fileName.equals("test-classes"))) {
                Path pom = path.getParent().getParent().resolve("pom.xml");
                if (Files.exists(pom)) {
                    Matcher matcher = Pattern.compile("<artifactId>([^<]+)</artifactId>")
                            .matcher(read(pom).replaceAll("(?s)<parent>.*?</parent>", ""));
                    if (matcher.find()) {
                        return new ClasspathEntry(path, matcher.group(1).trim(), pom);
                    }
                }
                return new ClasspathEntry(path, null, null);
            }
            if (fileName.endsWith(".jar") && path.getParent() != null && path.getParent().getParent() != null) {
                // reactor 内部依赖：<module>/target/<artifact>.jar，artifactId 与 pom 都取自模块目录。
                // 必须先判断这一种：`mvn verify` 在 package 之后把 reactor 依赖解析成 jar 而不是 target/classes，
                // 只认本地仓库布局时所有 chaos 模块的 pom 都是 null，isChaosLibrary() 全为 false，
                // chaosLibraryScenarios() 退化为 0 个场景。
                Path modulePom = path.getParent().getParent().resolve("pom.xml");
                if (Files.exists(modulePom)) {
                    Matcher matcher = Pattern.compile("<artifactId>([^<]+)</artifactId>")
                            .matcher(read(modulePom).replaceAll("(?s)<parent>.*?</parent>", ""));
                    if (matcher.find()) {
                        return new ClasspathEntry(path, matcher.group(1).trim(), modulePom);
                    }
                }
                // 本地仓库布局：<repo>/com/michael/<artifactId>/<version>/<artifactId>-<version>.jar
                String artifactId = path.getParent().getParent().getFileName().toString();
                Path pom = null;
                if (path.toString().replace(File.separatorChar, '/').contains("/com/michael/")) {
                    Path candidate = Paths.get(path.toString().replaceAll("\\.jar$", ".pom"));
                    pom = Files.exists(candidate) ? candidate : null;
                }
                return new ClasspathEntry(path, artifactId, pom);
            }
            return new ClasspathEntry(path, null, null);
        }

        boolean isChaosLibrary() {
            return pom != null && artifactId != null && artifactId.startsWith("chaos-")
                    && !NON_LIBRARY_CHAOS.contains(artifactId);
        }
    }
}
