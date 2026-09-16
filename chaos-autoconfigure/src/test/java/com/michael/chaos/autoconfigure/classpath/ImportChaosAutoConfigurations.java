package com.michael.chaos.autoconfigure.classpath;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

/**
 * 以与真实应用相同的方式（按类名导入、ASM 读取元数据、条件过滤与自动装配排序）导入自动装配候选。
 *
 * <p>候选列表来自 {@code META-INF/spring/<本注解全名>.imports}，由 {@link OptionalDependencyIsolationTest}
 * 为每个场景在临时目录中生成：全部 chaos 自动装配 + 当前隔离类路径中实际存在的 Spring Boot 基础自动装配。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ImportAutoConfiguration
public @interface ImportChaosAutoConfigurations {
}
