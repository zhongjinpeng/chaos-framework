package com.michael.chaos.architecture;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * 仓库模块模型：解析各 {@code pom.xml} 的直接依赖与源码 import，供依赖方向规则使用。
 *
 * <p>之所以解析 POM + 源码而不是用 ArchUnit 导入字节码：架构测试模块本身不依赖任何业务模块，
 * 在 {@code -pl chaos-architecture-tests} 单独运行、或并行构建时业务模块尚未编译的情况下也能稳定执行；
 * 模块级依赖方向（包括 optional / test scope）也只有 POM 才能准确表达。</p>
 */
final class ProjectModel {

    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([\\w.]+)(?:\\.\\*)?\\s*;");

    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");

    private final Path root;

    private final Map<String, Module> modules;

    private ProjectModel(Path root, Map<String, Module> modules) {
        this.root = root;
        this.modules = modules;
    }

    /**
     * 扫描仓库根目录下全部模块（排除 target）。
     */
    static ProjectModel load() throws IOException {
        Path root = projectRoot();
        Map<String, Module> modules = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path pom : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> Objects.equals(path.getFileName().toString(), "pom.xml"))
                    .filter(path -> !path.toString().contains(File.separator + "target" + File.separator))
                    // archetype-resources 下是生成项目的 Velocity 模板（artifactId 为 ${artifactId}），不是仓库模块。
                    .filter(path -> !path.toString().contains(File.separator + "archetype-resources" + File.separator))
                    .sorted()
                    .toList()) {
                Module module = parse(root, pom);
                modules.put(module.artifactId(), module);
            }
        }
        return new ProjectModel(root, modules);
    }

    Path root() {
        return root;
    }

    Map<String, Module> modules() {
        return modules;
    }

    Module module(String artifactId) {
        Module module = modules.get(artifactId);
        if (module == null) {
            throw new IllegalStateException("仓库中找不到模块 " + artifactId + "，请同步更新架构规则");
        }
        return module;
    }

    /**
     * 模块在仓库中的相对路径（使用 / 分隔）。
     */
    String relative(Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }

    /**
     * 返回模块 {@code src/main/java} 下全部 Java 源文件。
     */
    static List<Path> mainSources(Module module) throws IOException {
        return javaFiles(module.directory().resolve("src/main/java"));
    }

    static List<Path> javaFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 解析源文件中的 import（含 static import 的类型部分）。
     */
    static List<String> imports(Path javaFile) throws IOException {
        String source = Files.readString(javaFile, StandardCharsets.UTF_8);
        List<String> imports = new ArrayList<>();
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    /**
     * 解析源文件的 package 声明。
     */
    static Optional<String> packageOf(Path javaFile) throws IOException {
        Matcher matcher = PACKAGE.matcher(Files.readString(javaFile, StandardCharsets.UTF_8));
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    static Path projectRoot() {
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

    private static Module parse(Path root, Path pom) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Element project = factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
            String artifactId = childText(project, "artifactId");
            String packaging = Optional.ofNullable(childText(project, "packaging")).orElse("jar");
            List<Dependency> dependencies = new ArrayList<>();
            Element dependenciesElement = child(project, "dependencies");
            if (dependenciesElement != null) {
                for (Node node = dependenciesElement.getFirstChild(); node != null; node = node.getNextSibling()) {
                    if (node instanceof Element dependency && Objects.equals(dependency.getTagName(), "dependency")) {
                        dependencies.add(new Dependency(
                                childText(dependency, "groupId"),
                                childText(dependency, "artifactId"),
                                Optional.ofNullable(childText(dependency, "scope")).orElse("compile"),
                                Boolean.parseBoolean(childText(dependency, "optional"))
                        ));
                    }
                }
            }
            return new Module(artifactId, packaging, pom.getParent(), root.relativize(pom.getParent())
                    .toString().replace(File.separatorChar, '/'), List.copyOf(dependencies));
        } catch (Exception ex) {
            throw new IOException("POM 解析失败: " + pom, ex);
        }
    }

    private static Element child(Element element, String tagName) {
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element childElement && Objects.equals(childElement.getTagName(), tagName)) {
                return childElement;
            }
        }
        return null;
    }

    private static String childText(Element element, String tagName) {
        Element child = child(element, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    /**
     * 模块描述。
     *
     * @param artifactId artifactId
     * @param packaging 打包类型
     * @param directory 模块目录
     * @param path 相对仓库根目录的路径
     * @param dependencies 直接声明的依赖（不含 dependencyManagement）
     */
    record Module(String artifactId, String packaging, Path directory, String path, List<Dependency> dependencies) {

        /**
         * 非 test scope 的 chaos 内部依赖（含 optional）。
         */
        List<Dependency> productionChaosDependencies() {
            return dependencies.stream()
                    .filter(Dependency::isChaos)
                    .filter(dependency -> !dependency.isTest())
                    .toList();
        }

        boolean isStarter() {
            return path.startsWith("chaos-starters/") && !"pom".equals(packaging);
        }
    }

    /**
     * POM 直接依赖。
     *
     * @param groupId groupId
     * @param artifactId artifactId
     * @param scope scope，缺省为 compile
     * @param optional 是否 optional
     */
    record Dependency(String groupId, String artifactId, String scope, boolean optional) {

        boolean isChaos() {
            return Objects.equals(groupId, "com.michael");
        }

        boolean isTest() {
            return Objects.equals(scope, "test");
        }

        @Override
        public String toString() {
            return groupId + ":" + artifactId + " (" + scope + (optional ? ", optional" : "") + ")";
        }
    }
}
