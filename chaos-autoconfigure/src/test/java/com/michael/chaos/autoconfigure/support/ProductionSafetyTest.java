package com.michael.chaos.autoconfigure.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 生产安全检查测试。
 */
class ProductionSafetyTest {

    private final ApplicationContextRunner enforcerRunner = new ApplicationContextRunner()
            .withUserConfiguration(EnforcerConfiguration.class, ProxiedUnsafeConfiguration.class);

    private final ApplicationContextRunner staticCheckRunner = new ApplicationContextRunner()
            .withUserConfiguration(StaticCheckConfiguration.class);

    /**
     * 非生产模式不检查。
     */
    @Test
    void shouldIgnoreNonProductionEnvironment() {
        enforcerRunner.run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * 生产模式默认 fail-fast，且能识别被 CGLIB 代理的用户配置类上的 @UnsafeForProduction。
     */
    @Test
    void shouldFailFastForProxiedConfigurationInProduction() {
        enforcerRunner.withPropertyValues("chaos.production-safety.production-mode=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("unsafe-sample"));
    }

    /**
     * 失败信息使用"问题 / 原因 / 怎么修"格式，并指出注册危险实现的 Bean 名称。
     */
    @Test
    void failureMessageShouldBeActionableAndNameTheBean() {
        enforcerRunner.withPropertyValues("chaos.production-safety.production-mode=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .isInstanceOf(ChaosDiagnosticException.class)
                        .hasMessageStartingWith("问题：生产安全检查未通过")
                        .hasMessageContaining("（Bean：unsafeSample）")
                        .hasMessageContaining("chaos-redis-starter")
                        .hasMessageContaining("chaos.production-safety.fail-fast=false"));
    }

    /**
     * prd profile 默认也视为生产环境。
     */
    @Test
    void shouldTreatPrdProfileAsProduction() {
        enforcerRunner.withPropertyValues("spring.profiles.active=prd")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 关闭 fail-fast 时只告警。
     */
    @Test
    void shouldOnlyWarnWhenFailFastDisabled() {
        enforcerRunner.withPropertyValues(
                        "chaos.production-safety.production-mode=true",
                        "chaos.production-safety.fail-fast=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * 迁移期逃生开关可以放行。
     */
    @Test
    void shouldAllowUnsafeDefaultsWhenExplicitlyEnabled() {
        enforcerRunner.withPropertyValues(
                        "chaos.production-safety.production-mode=true",
                        "chaos.production-safety.allow-unsafe-defaults=true")
                .run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * 历史方法 warnUnsafeDefaultBeans 同样遵循 fail-fast。
     */
    @Test
    void legacyWarnMethodShouldFollowFailFast() {
        staticCheckRunner.withPropertyValues("chaos.production-safety.production-mode=true")
                .run(context -> assertThat(context).hasFailed());
        staticCheckRunner.withPropertyValues(
                        "chaos.production-safety.production-mode=true",
                        "chaos.production-safety.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    static class UnsafeSample {
    }

    @Configuration(proxyBeanMethods = false)
    static class EnforcerConfiguration {

        @Bean
        SmartInitializingSingleton productionSafetyEnforcer(
                Environment environment,
                ConfigurableListableBeanFactory beanFactory) {
            return new ProductionSafetyEnforcer(environment, beanFactory);
        }
    }

    /**
     * 默认 proxyBeanMethods=true，会生成 CGLIB 代理子类。
     */
    @Configuration
    static class ProxiedUnsafeConfiguration {

        @Bean
        @UnsafeForProduction(value = UnsafeSample.class, message = "unsafe-sample")
        UnsafeSample unsafeSample() {
            return new UnsafeSample();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StaticCheckConfiguration {

        @Bean
        UnsafeSample unsafeSample() {
            return new UnsafeSample();
        }

        @Bean
        SmartInitializingSingleton checker(Environment environment, ConfigurableListableBeanFactory beanFactory) {
            return () -> ProductionSafety.warnUnsafeDefaultBeans(
                    environment,
                    beanFactory,
                    Map.of(UnsafeSample.class.getName(), "unsafe-static")
            );
        }
    }
}
