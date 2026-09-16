package com.michael.chaos.autoconfigure.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * 校验 {@code chaos-docs/.../docs/templates} 下的配置模板与代码中的配置属性保持一致。
 *
 * <p>模板是使用方复制粘贴的起点，一旦属性改名或删除而模板没跟着改，使用方拿到的就是“看起来能用、实际不生效”的配置。
 * 因此每个模板都要通过两层检查：</p>
 * <ol>
 *     <li>每个 {@code chaos.*} 配置项必须出现在 classpath 上的 Spring 配置元数据中（包括 additional 元数据声明的
 *     {@code chaos.production-safety.*} 等通过 Environment 读取的配置），否则视为未知配置；</li>
 *     <li>按 {@code @ConfigurationProperties} 前缀把模板值真实绑定到对应属性类，并开启“不允许未绑定元素”，
 *     捕获类型错误（例如把 Duration 写成非法字符串）以及嵌套列表元素中的未知字段。</li>
 * </ol>
 *
 * <p>属性类通过扫描 {@code com.michael.chaos} 包自动发现，新增属性类无需修改本测试。
 * 模板中的 {@code ${VAR:default}} 占位符按默认值绑定，没有默认值的占位符按普通字符串绑定——
 * 因此类型化配置（Duration、数字、布尔值）不要写成无默认值的占位符。</p>
 */
class ConfigurationTemplatesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    private static final Pattern INDEX = Pattern.compile("\\[\\d+]");

    /**
     * 场景开发模板承诺“最小配置”：chaos.* 叶子配置项超过这个数量说明默认值设计或模板需要重新审视。
     */
    private static final int MAX_SCENARIO_DEV_CHAOS_KEYS = 10;

    private static Map<String, String> metadataTypes;

    private static Map<String, Class<?>> propertiesClasses;

    @BeforeAll
    static void loadMetadataAndPropertiesClasses() throws Exception {
        metadataTypes = loadMetadataTypes();
        propertiesClasses = discoverPropertiesClasses();
    }

    /**
     * 模板目录必须存在，且三个场景都同时提供开发与生产模板。
     */
    @Test
    void scenarioTemplatesShouldExist() {
        Path templates = templatesDirectory();
        for (String scenario : List.of("web-service", "gateway", "auth-server")) {
            assertThat(templates.resolve(scenario).resolve("application.yml")).as(scenario + " 开发模板").isRegularFile();
            assertThat(templates.resolve(scenario).resolve("application-prod.yml")).as(scenario + " 生产模板").isRegularFile();
        }
        assertThat(propertiesClasses).as("应能扫描到框架的 @ConfigurationProperties 类").containsKeys(
                "chaos.web", "chaos.security", "chaos.authorization", "chaos.gateway", "chaos.mybatis", "chaos.storage");
    }

    /**
     * 模板中的每个 chaos.* 配置项都必须是真实存在的配置属性。
     */
    @Test
    void templateKeysShouldBeKnownConfigurationProperties() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path template : templateFiles()) {
            for (String key : chaosProperties(template).keySet()) {
                if (!isKnown(key)) {
                    violations.add(relative(template) + ": " + key
                            + "（未在配置元数据中找到；属性可能已改名或删除，或未使用 kebab-case 书写）");
                }
            }
        }

        assertThat(violations).as("配置模板中存在未知配置项").isEmpty();
    }

    /**
     * 模板值必须能绑定到属性类：类型正确，且列表 / 嵌套对象中没有未知字段。
     */
    @Test
    void templateValuesShouldBindToPropertiesClasses() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path template : templateFiles()) {
            Map<String, Object> properties = resolvePlaceholders(chaosProperties(template));
            for (Map.Entry<String, Class<?>> entry : propertiesClasses.entrySet()) {
                Map<String, Object> scoped = scopedProperties(entry.getKey(), properties);
                if (scoped.isEmpty()) {
                    continue;
                }
                Binder binder = new Binder(new MapConfigurationPropertySource(scoped));
                BindHandler handler = new NoUnboundElementsBindHandler(BindHandler.DEFAULT);
                try {
                    binder.bind(entry.getKey(), Bindable.of(entry.getValue()), handler);
                } catch (BindException ex) {
                    violations.add(relative(template) + ": 绑定 " + entry.getKey() + " → "
                            + entry.getValue().getSimpleName() + " 失败：" + rootCauseMessage(ex));
                }
            }
        }

        assertThat(violations).as("配置模板无法绑定到属性类").isEmpty();
    }

    /**
     * 场景开发模板必须保持“最小配置”。
     */
    @Test
    void scenarioDevTemplatesShouldStayMinimal() throws IOException {
        for (String scenario : List.of("web-service", "gateway", "auth-server")) {
            Path template = templatesDirectory().resolve(scenario).resolve("application.yml");
            assertThat(chaosProperties(template))
                    .as(relative(template) + " 的 chaos.* 配置项应不超过 " + MAX_SCENARIO_DEV_CHAOS_KEYS + " 个")
                    .hasSizeLessThanOrEqualTo(MAX_SCENARIO_DEV_CHAOS_KEYS);
        }
    }

    /**
     * 判断配置项是否已知：精确匹配，或落在集合 / Map / 嵌套对象类型属性之下（交给绑定检查进一步校验）。
     */
    private static boolean isKnown(String key) {
        String normalized = INDEX.matcher(key).replaceAll("");
        if (metadataTypes.containsKey(normalized)) {
            return true;
        }
        return metadataTypes.entrySet().stream()
                .filter(entry -> normalized.startsWith(entry.getKey() + "."))
                .anyMatch(entry -> isContainerType(entry.getValue()));
    }

    private static boolean isContainerType(String type) {
        return type != null && (type.startsWith("java.util.List<") || type.startsWith("java.util.Map<")
                || type.startsWith("java.util.Set<") || type.startsWith("java.util.Collection<"));
    }

    /**
     * 取出属于某个前缀的配置，排除归属于更具体前缀的属性类的配置（如 chaos.storage 不包含 chaos.storage.minio.*）。
     */
    private static Map<String, Object> scopedProperties(String prefix, Map<String, Object> properties) {
        List<String> moreSpecific = propertiesClasses.keySet().stream()
                .filter(other -> other.startsWith(prefix + "."))
                .toList();
        Map<String, Object> scoped = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            boolean inScope = key.startsWith(prefix + ".");
            boolean ownedByOther = moreSpecific.stream().anyMatch(other -> key.startsWith(other + "."));
            if (inScope && !ownedByOther) {
                scoped.put(key, value);
            }
        });
        return scoped;
    }

    private static Map<String, Object> resolvePlaceholders(Map<String, Object> properties) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            if (value instanceof String text) {
                Matcher matcher = PLACEHOLDER.matcher(text);
                StringBuilder builder = new StringBuilder();
                while (matcher.find()) {
                    String replacement = matcher.group(2) != null ? matcher.group(2) : "placeholder-" + matcher.group(1);
                    matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
                }
                matcher.appendTail(builder);
                resolved.put(key, builder.toString());
            } else {
                resolved.put(key, value);
            }
        });
        return resolved;
    }

    private static Map<String, Object> chaosProperties(Path template) throws IOException {
        Map<String, Object> properties = new TreeMap<>();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(template.getFileName().toString(), new FileSystemResource(template));
        for (PropertySource<?> source : sources) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith("chaos.")) {
                        Object value = enumerable.getProperty(name);
                        properties.put(name, value == null ? null : value.toString());
                    }
                }
            }
        }
        return properties;
    }

    private static Map<String, String> loadMetadataTypes() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> types = new TreeMap<>();
        Enumeration<URL> resources = ConfigurationTemplatesTest.class.getClassLoader()
                .getResources("META-INF/spring-configuration-metadata.json");
        while (resources.hasMoreElements()) {
            try (InputStream input = resources.nextElement().openStream()) {
                JsonNode properties = mapper.readTree(input).path("properties");
                for (JsonNode property : properties) {
                    String name = property.path("name").asText();
                    if (name.startsWith("chaos.")) {
                        types.put(name, property.path("type").asText(null));
                    }
                }
            }
        }
        return types;
    }

    private static Map<String, Class<?>> discoverPropertiesClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));
        Map<String, Class<?>> classes = new TreeMap<>(Comparator.naturalOrder());
        for (var candidate : scanner.findCandidateComponents("com.michael.chaos")) {
            Class<?> type = ClassUtils.forName(Objects.requireNonNull(candidate.getBeanClassName()),
                    ConfigurationTemplatesTest.class.getClassLoader());
            ConfigurationProperties annotation = type.getAnnotation(ConfigurationProperties.class);
            String prefix = annotation.prefix().isEmpty() ? annotation.value() : annotation.prefix();
            if (prefix.startsWith("chaos.")) {
                classes.put(prefix, type);
            }
        }
        return classes;
    }

    private static List<Path> templateFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(templatesDirectory())) {
            List<Path> files = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
            assertThat(files).as("配置模板目录中应至少有一个 yml 模板").isNotEmpty();
            return files;
        }
    }

    private static Path templatesDirectory() {
        return projectRoot().resolve("chaos-docs/src/main/resources/docs/templates");
    }

    private static Path projectRoot() {
        String multiModuleRoot = System.getProperty("maven.multiModuleProjectDirectory");
        Path start = multiModuleRoot != null && !multiModuleRoot.isBlank()
                ? Path.of(multiModuleRoot)
                : Path.of("").toAbsolutePath();
        Optional<Path> root = Stream.iterate(start.toAbsolutePath().normalize(), Objects::nonNull, Path::getParent)
                .filter(path -> Files.isDirectory(path.resolve("chaos-docs")))
                .findFirst();
        return root.orElseThrow(() -> new IllegalStateException("找不到包含 chaos-docs 的仓库根目录: " + start));
    }

    private static String relative(Path template) {
        return templatesDirectory().relativize(template).toString();
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        StringBuilder messages = new StringBuilder(String.valueOf(throwable.getMessage()));
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            messages.append(" ← ").append(current.getMessage());
        }
        return messages.toString();
    }
}
