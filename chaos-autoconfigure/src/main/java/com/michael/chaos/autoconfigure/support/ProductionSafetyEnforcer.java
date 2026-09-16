package com.michael.chaos.autoconfigure.support;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * 统一生产安全检查器。
 *
 * <p>扫描所有配置类中标注了 {@link UnsafeForProduction} 的 {@code @Bean} 方法，
 * 在生产模式下如果容器中存在对应类型的 Bean 实例，默认阻断启动（见 {@link ProductionSafety}）。</p>
 *
 * <p>各 autoconfigure 模块只需在 @Bean 方法上标注 {@code @UnsafeForProduction}，
 * 无需手写 {@link SmartInitializingSingleton}。</p>
 */
public class ProductionSafetyEnforcer implements SmartInitializingSingleton {

    private final Environment environment;
    private final ConfigurableListableBeanFactory beanFactory;

    public ProductionSafetyEnforcer(Environment environment, ConfigurableListableBeanFactory beanFactory) {
        this.environment = environment;
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!ProductionSafety.isProductionEnvironment(environment)) {
            return;
        }
        Set<String> violations = new LinkedHashSet<>();
        Set<Class<?>> scanned = new LinkedHashSet<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Object bean = beanFactory.getSingleton(beanName);
            if (bean == null) {
                continue;
            }
            // 用户配置类默认被 CGLIB 代理（proxyBeanMethods=true），代理子类覆盖的方法上没有 @Bean 注解，
            // 必须还原为用户类再扫描，否则会漏检。
            Class<?> userClass = ClassUtils.getUserClass(bean.getClass());
            if (scanned.add(userClass)) {
                scanConfigurationClass(userClass, violations);
            }
        }
        ProductionSafety.report(environment, new ArrayList<>(violations));
    }

    private void scanConfigurationClass(Class<?> configClass, Set<String> violations) {
        List<Method> methods = new ArrayList<>();
        ReflectionUtils.doWithMethods(configClass, methods::add,
                method -> AnnotationUtils.findAnnotation(method, Bean.class) != null);
        for (Method method : methods) {
            UnsafeForProduction annotation = AnnotationUtils.findAnnotation(method, UnsafeForProduction.class);
            if (annotation == null) {
                continue;
            }
            for (Class<?> unsafeType : annotation.value()) {
                String[] beanNames = beanFactory.getBeanNamesForType(unsafeType, true, false);
                if (beanNames.length > 0) {
                    violations.add(ProductionSafety.withBeanNames(annotation.message(), beanNames));
                    break;
                }
            }
        }
    }
}
