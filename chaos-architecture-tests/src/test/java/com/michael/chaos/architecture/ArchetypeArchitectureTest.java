package com.michael.chaos.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * archetype 模板治理。
 *
 * <p>archetype 生成的配置必须与 chaos-docs/templates 保持一致：模板由 {@code ConfigurationTemplatesTest} 校验配置项真实存在，
 * archetype 复用同一份内容，才能保证“文档里的最小配置”和“生成出来的项目”不会各说各话。</p>
 *
 * <p>另外 archetype-resources 会经过 Velocity 渲染，{@code ##} 会被当作行注释删除、{@code ${...}} 会被当作变量，
 * 这两类内容只能出现在 archetype-metadata.xml 声明为 {@code filtered="false"} 的文件中。</p>
 */
class ArchetypeArchitectureTest {

    /**
     * archetype 模块 → docs/templates 场景目录。
     */
    private static final Map<String, String> SCENARIOS = Map.of(
            "chaos-archetype-web-service", "web-service",
            "chaos-archetype-gateway", "gateway",
            "chaos-archetype-auth-server", "auth-server"
    );

    private static final Pattern APPLICATION_NAME = Pattern.compile("(?m)^(    name: ).+$");

    private static final Pattern VELOCITY_VARIABLES = Pattern.compile("\\$\\{(package|groupId|artifactId|version|chaosVersion)}");

    /**
     * 脚手架生成的配置必须与文档模板一致，否则使用方照文档改配置会发现对不上。
     */
    @Test
    void archetypeConfigurationShouldMatchDocumentedTemplates() throws IOException {
        Path root = ProjectModel.projectRoot();
        for (Map.Entry<String, String> scenario : SCENARIOS.entrySet()) {
            Path resources = root.resolve("chaos-archetypes/" + scenario.getKey()
                    + "/src/main/resources/archetype-resources/src/main/resources");
            Path templates = root.resolve("chaos-docs/src/main/resources/docs/templates/" + scenario.getValue());

            assertEquals(
                    read(templates.resolve("application-prod.yml")),
                    read(resources.resolve("application-prod.yml")),
                    scenario.getKey() + " 的 application-prod.yml 必须与 docs/templates/" + scenario.getValue() + " 完全一致"
            );
            assertEquals(
                    normalizeApplicationName(read(templates.resolve("application.yml"))),
                    normalizeApplicationName(read(resources.resolve("application.yml"))),
                    scenario.getKey() + " 的 application.yml 除 spring.application.name 外必须与 docs/templates/"
                            + scenario.getValue() + " 一致"
            );
            assertTrue(read(resources.resolve("application.yml")).contains("    name: ${artifactId}"),
                    scenario.getKey() + " 的 application.yml 应使用 ${artifactId} 作为应用名");
        }
    }

    /**
     * 生成的工程要继承 chaos-boot-parent 并指向当前框架版本，避免一生成就是旧版本。
     */
    @Test
    void archetypesShouldUseBootParentAndCurrentChaosVersion() throws IOException {
        Path root = ProjectModel.projectRoot();
        for (String archetype : SCENARIOS.keySet()) {
            Path module = root.resolve("chaos-archetypes/" + archetype);
            String pom = read(module.resolve("src/main/resources/archetype-resources/pom.xml"));
            String metadata = read(module.resolve("src/main/resources/META-INF/maven/archetype-metadata.xml"));
            assertTrue(pom.contains("<artifactId>chaos-boot-parent</artifactId>") && pom.contains("<version>${chaosVersion}</version>"),
                    archetype + " 生成的项目必须以 chaos-boot-parent 为 parent，版本取 chaosVersion");
            assertTrue(metadata.contains("<requiredProperty key=\"chaosVersion\">")
                            && metadata.contains("<defaultValue>@project.version@</defaultValue>"),
                    archetype + " 的 chaosVersion 默认值必须在构建时替换为当前版本");
            assertTrue(Files.exists(module.resolve("src/main/resources/archetype-resources/README.md")),
                    archetype + " 必须为生成项目提供 README.md");
        }
    }

    /**
     * archetype 资源是 Velocity 模板，`##` 和未转义的 `${}` 会在生成时被吃掉，必须在构建期拦住。
     */
    @Test
    void velocityRenderedTemplatesShouldNotContainVelocityTraps() throws IOException {
        Path root = ProjectModel.projectRoot();
        List<String> violations = new ArrayList<>();
        for (String archetype : SCENARIOS.keySet()) {
            Path templates = root.resolve("chaos-archetypes/" + archetype + "/src/main/resources/archetype-resources");
            try (Stream<Path> paths = Files.walk(templates)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    String name = file.getFileName().toString();
                    if (name.equals("application-prod.yml") || name.equals("README.md")) {
                        // archetype-metadata.xml 中声明为 filtered="false"，原样拷贝。
                        continue;
                    }
                    String content = read(file);
                    String withoutVariables = VELOCITY_VARIABLES.matcher(content).replaceAll("");
                    String relative = root.relativize(file).toString();
                    if (content.contains("##")) {
                        violations.add(relative + " 含有 ##，会被 Velocity 当作注释删除");
                    }
                    if (withoutVariables.contains("${")) {
                        violations.add(relative + " 含有非 archetype 变量的 ${...}，会被 Velocity 误解析");
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "archetype 模板存在 Velocity 渲染风险:\n" + String.join("\n", violations));
    }

    private static String normalizeApplicationName(String yaml) {
        Matcher matcher = APPLICATION_NAME.matcher(yaml);
        return matcher.find() ? matcher.replaceFirst("$1<application-name>") : yaml;
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
