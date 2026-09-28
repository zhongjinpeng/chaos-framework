package com.chaos.test.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * DDD 分层与对象命名的轻量源码约束。
 *
 * <p>该断言不要求业务项目引入字节码扫描框架，适用于模块化单体和普通 Maven 服务。
 * 业务项目在测试中传入 {@code src/main/java} 与业务根包即可。</p>
 */
public final class DddArchitectureAssertions {

    private static final List<String> DOMAIN_FORBIDDEN_IMPORTS = List.of(
            "org.springframework.",
            "jakarta.persistence.",
            "jakarta.servlet.",
            "com.baomidou.",
            "org.apache.ibatis.",
            ".infrastructure.",
            ".interfaces."
    );

    private static final Set<String> DOMAIN_FORBIDDEN_SUFFIXES = Set.of(
            "DO", "PO", "DTO", "Request", "Response", "Command", "Query", "Result", "Mapper", "Converter",
            "RepositoryImpl", "Controller"
    );

    private DddArchitectureAssertions() {
    }

    /**
     * 校验约定式 DDD 目录、依赖方向和边界对象位置。
     *
     * @param javaSourceRoot Maven 主源码目录
     * @param basePackage 业务根包，例如 {@code com.example.order}
     */
    public static void assertLayeredArchitecture(Path javaSourceRoot, String basePackage) {
        Path boundedContext = javaSourceRoot.resolve(basePackage.replace('.', '/'));
        List<String> violations = new ArrayList<>();
        if (!Files.isDirectory(boundedContext)) {
            violations.add("业务根包不存在: " + boundedContext);
        } else {
            inspectDomain(boundedContext.resolve("domain"), violations);
            inspectBoundaryPackage(boundedContext.resolve("application/command"), "Command", violations);
            inspectBoundaryPackage(boundedContext.resolve("application/query"), "Query", violations);
            inspectBoundaryPackage(boundedContext.resolve("application/result"), "Result", violations);
            inspectApplicationServices(boundedContext.resolve("application"), violations);
            inspectBoundaryPackage(boundedContext.resolve("interfaces"), violations);
            inspectPersistencePackage(boundedContext.resolve("infrastructure/persistence"), violations);
        }
        assertTrue(violations.isEmpty(), () -> "DDD 架构约束不满足:\n" + String.join("\n", violations));
    }

    private static void inspectDomain(Path domain, List<String> violations) {
        for (Path file : javaFiles(domain, violations)) {
            String source = read(file, violations);
            String name = typeName(file);
            DOMAIN_FORBIDDEN_SUFFIXES.stream()
                    .filter(name::endsWith)
                    .findFirst()
                    .ifPresent(suffix -> violations.add(file + " 领域类型不应使用技术后缀 " + suffix));
            source.lines().map(String::trim).filter(line -> line.startsWith("import ")).forEach(line ->
                    DOMAIN_FORBIDDEN_IMPORTS.stream()
                            .filter(line::contains)
                            .forEach(prefix -> violations.add(file + " 领域层禁止依赖 " + prefix)));
        }
    }

    private static void inspectBoundaryPackage(Path directory, String suffix, List<String> violations) {
        for (Path file : javaFiles(directory, violations)) {
            if (!typeName(file).endsWith(suffix)) {
                violations.add(file + " 应以 " + suffix + " 结尾");
            }
        }
    }

    private static void inspectBoundaryPackage(Path interfaces, List<String> violations) {
        for (Path file : javaFiles(interfaces, violations)) {
            String normalized = file.toString().replace('\\', '/');
            String name = typeName(file);
            if (normalized.contains("/request/") && !name.endsWith("Request")) {
                violations.add(file + " request 包类型应以 Request 结尾");
            }
            if (normalized.contains("/response/") && !name.endsWith("Response")) {
                violations.add(file + " response 包类型应以 Response 结尾");
            }
            if (name.endsWith("Controller") && read(file, violations).contains(" record ")) {
                violations.add(file + " Controller 不应内嵌声明 Request/Response");
            }
        }
    }

    private static void inspectApplicationServices(Path application, List<String> violations) {
        for (Path file : javaFiles(application, violations)) {
            if (typeName(file).endsWith("ApplicationService") && read(file, violations).contains(" record ")) {
                violations.add(file + " ApplicationService 不应内嵌声明 Command/Query/Result 或辅助记录");
            }
        }
    }

    private static void inspectPersistencePackage(Path persistence, List<String> violations) {
        inspectPathSuffix(persistence.resolve("po"), "PO", violations);
        inspectPathSuffix(persistence.resolve("mapper"), "Mapper", violations);
        inspectPathSuffix(persistence.resolve("converter"), "Converter", violations);
        inspectPathSuffix(persistence.resolve("repository"), "RepositoryImpl", violations);
    }

    private static void inspectPathSuffix(Path directory, String suffix, List<String> violations) {
        for (Path file : javaFiles(directory, violations)) {
            if (!typeName(file).endsWith(suffix)) {
                violations.add(file + " 应以 " + suffix + " 结尾");
            }
        }
    }

    private static List<Path> javaFiles(Path directory, List<String> violations) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            violations.add("无法扫描源码目录 " + directory + ": " + ex.getMessage());
            return List.of();
        }
    }

    private static String read(Path file, List<String> violations) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            violations.add("无法读取源码 " + file + ": " + ex.getMessage());
            return "";
        }
    }

    private static String typeName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }
}
