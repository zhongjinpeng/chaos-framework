package com.michael.chaos.autoconfigure.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

/**
 * 诊断规则可以读取的上下文。
 *
 * @param environment Spring 环境
 * @param beanFactory Bean 工厂（只读使用，不要触发 Bean 创建）
 * @param productionMode 是否被识别为生产模式
 * @param enabledFeatures 已启用的功能名
 */
public record ChaosDiagnosticContext(
        Environment environment,
        ListableBeanFactory beanFactory,
        boolean productionMode,
        Set<String> enabledFeatures) {

    /**
     * 规范化参数。
     */
    public ChaosDiagnosticContext {
        enabledFeatures = Set.copyOf(enabledFeatures);
    }

    /**
     * 功能是否启用。
     */
    public boolean isEnabled(String feature) {
        return enabledFeatures.contains(feature);
    }

    /**
     * 读取字符串配置，未配置或空白时返回空字符串。
     */
    public String property(String name) {
        String value = environment.getProperty(name);
        return value == null ? "" : value.trim();
    }

    /**
     * 读取布尔配置。
     */
    public boolean booleanProperty(String name, boolean defaultValue) {
        return environment.getProperty(name, Boolean.class, defaultValue);
    }

    /**
     * 读取列表配置（兼容逗号分隔与索引写法）。
     */
    public List<String> listProperty(String name) {
        return Binder.get(environment).bind(name, Bindable.listOf(String.class)).orElseGet(ArrayList::new);
    }

    /**
     * 容器中是否存在指定类型（按类名，类不存在时视为不存在）的 Bean，不会触发 Bean 创建。
     */
    public boolean hasBeanOfType(String className) {
        return !beanNamesOfType(className).isEmpty();
    }

    /**
     * 按类名查找 Bean 名称，类不存在时返回空列表，不会触发 Bean 创建。
     */
    public List<String> beanNamesOfType(String className) {
        ClassLoader classLoader = ChaosDiagnosticContext.class.getClassLoader();
        if (!ClassUtils.isPresent(className, classLoader)) {
            return List.of();
        }
        Class<?> type = ClassUtils.resolveClassName(className, classLoader);
        return List.of(beanFactory.getBeanNamesForType(type, true, false));
    }
}
