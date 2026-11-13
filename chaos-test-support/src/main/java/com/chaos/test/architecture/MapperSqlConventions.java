package com.chaos.test.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Mapper SQL 约定断言：SQL 只写在 XML，Mapper 接口按全限定名绑定同名 XML。
 *
 * <p>业务项目在测试中传入 {@code src/main/java} 与 {@code src/main/resources/mapper} 即可。</p>
 */
public final class MapperSqlConventions {

    private static final Pattern SQL_ANNOTATION = Pattern.compile(
            "@(Select|Insert|Update|Delete|SelectProvider|InsertProvider|UpdateProvider|DeleteProvider)\\b");
    private static final Pattern NAMESPACE = Pattern.compile("<mapper\\s+namespace=\"([^\"]+)\"");

    private MapperSqlConventions() {
    }

    /**
     * 校验 Mapper 接口没有声明 SQL 注解。
     *
     * @param javaSourceRoot Maven 主源码目录，例如 {@code src/main/java}
     */
    public static void assertNoSqlAnnotations(Path javaSourceRoot) {
        List<String> violations = new ArrayList<>();
        for (Path file : mapperFiles(javaSourceRoot, violations)) {
            for (String line : read(file, violations).lines().toList()) {
                if (SQL_ANNOTATION.matcher(line).find()) {
                    violations.add(file + ": " + line.trim());
                }
            }
        }
        assertTrue(violations.isEmpty(),
                () -> "Mapper 接口不得声明 SQL 注解，SQL 只能写在 XML:\n" + String.join("\n", violations));
    }

    /**
     * 校验每个 Mapper 接口都有全限定名一致的 XML 命名空间。
     *
     * @param javaSourceRoot Maven 主源码目录，例如 {@code src/main/java}
     * @param mapperResources Mapper XML 目录，例如 {@code src/main/resources/mapper}
     */
    public static void assertEveryMapperHasXml(Path javaSourceRoot, Path mapperResources) {
        List<String> violations = new ArrayList<>();
        List<String> namespaces = new ArrayList<>();
        for (Path xml : sourceFiles(mapperResources, violations, path -> path.toString().endsWith(".xml"))) {
            Matcher matcher = NAMESPACE.matcher(read(xml, violations));
            if (!matcher.find()) {
                violations.add(xml + " 缺少 mapper namespace");
            } else {
                namespaces.add(matcher.group(1));
            }
        }
        for (Path file : mapperFiles(javaSourceRoot, violations)) {
            String expected = javaSourceRoot.relativize(file).toString()
                    .replaceAll("[\\\\/]", ".")
                    .replaceAll("\\.java$", "");
            if (!namespaces.contains(expected)) {
                violations.add(file + " 缺少命名空间为 " + expected + " 的 Mapper XML");
            }
        }
        assertTrue(violations.isEmpty(),
                () -> "Mapper XML 约定不满足:\n" + String.join("\n", violations));
    }

    private static List<Path> mapperFiles(Path root, List<String> violations) {
        return sourceFiles(root, violations, path -> {
            String normalized = path.toString().replace('\\', '/');
            return normalized.endsWith("Mapper.java") && normalized.contains("/persistence/mapper/");
        });
    }

    private static List<Path> sourceFiles(Path root, List<String> violations, Predicate<Path> filter) {
        if (!Files.isDirectory(root)) {
            violations.add("目录不存在: " + root);
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).filter(filter).toList();
        } catch (IOException ex) {
            violations.add("读取目录失败: " + root + " (" + ex.getMessage() + ")");
            return List.of();
        }
    }

    private static String read(Path file, List<String> violations) {
        try {
            return Files.readString(file);
        } catch (IOException ex) {
            violations.add("读取文件失败: " + file);
            return "";
        }
    }
}
