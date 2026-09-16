package com.michael.chaos.autoconfigure.classpath;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.assertj.ApplicationContextAssertProvider;
import org.springframework.boot.test.context.runner.AbstractApplicationContextRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 在隔离类加载器内部执行的探针。
 *
 * <p>本类及 Spring、chaos 自动装配类都由隔离的 {@link java.net.URLClassLoader} 定义，被排除的 jar 在该加载器中
 * 物理不存在，因此能暴露真实的 {@code NoClassDefFoundError}。{@code FilteredClassLoader} 只影响条件判断，
 * 父加载器仍能解析被“隐藏”的类，无法发现这类问题。</p>
 *
 * <p>返回值只使用 {@code String}（由启动类加载器定义），避免跨类加载器传递对象。</p>
 */
public final class ClasspathIsolationProbe {

    private ClasspathIsolationProbe() {
    }

    /**
     * 启动导入全部 chaos 自动装配的上下文。
     *
     * @param webType {@code none}、{@code servlet} 或 {@code reactive}
     * @param properties 额外的环境属性
     * @return {@code null} 表示启动成功；否则为失败原因链描述
     */
    public static String run(String webType, String[] properties) {
        ClassLoader loader = ClasspathIsolationProbe.class.getClassLoader();
        List<String> failure = new ArrayList<>();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            switch (webType) {
                case "servlet" -> runWith(new WebApplicationContextRunner(), loader, properties, failure);
                case "reactive" -> runWith(new ReactiveWebApplicationContextRunner(), loader, properties, failure);
                default -> runWith(new ApplicationContextRunner(), loader, properties, failure);
            }
        } catch (Throwable ex) {
            failure.add(describe(ex));
        } finally {
            thread.setContextClassLoader(previous);
        }
        return failure.isEmpty() ? null : String.join("\n", failure);
    }

    private static <S extends AbstractApplicationContextRunner<S, ?, A>, A extends ApplicationContextAssertProvider<?>>
            void runWith(S runner, ClassLoader loader, String[] properties, List<String> failure) {
        runner.withClassLoader(loader)
                .withPropertyValues(properties)
                .withUserConfiguration(ProbeConfiguration.class)
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    if (startupFailure != null) {
                        failure.add(describe(startupFailure));
                    }
                });
    }

    private static String describe(Throwable ex) {
        StringBuilder builder = new StringBuilder();
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 12) {
            builder.append(depth == 0 ? "" : "\n  caused by: ")
                    .append(current.getClass().getName())
                    .append(": ")
                    .append(current.getMessage());
            current = current.getCause() == current ? null : current.getCause();
            depth++;
        }
        return builder.toString();
    }

    /**
     * 探针入口配置：导入测试为当前场景生成的自动装配候选列表。
     */
    @Configuration(proxyBeanMethods = false)
    @ImportChaosAutoConfigurations
    static class ProbeConfiguration {
    }
}
