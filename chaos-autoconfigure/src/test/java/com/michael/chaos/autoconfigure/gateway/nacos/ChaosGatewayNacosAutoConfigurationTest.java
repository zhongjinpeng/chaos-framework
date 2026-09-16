package com.michael.chaos.autoconfigure.gateway.nacos;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.api.config.ConfigService;
import com.michael.chaos.gateway.nacos.ChaosGatewayNacosRouteProperties;
import com.michael.chaos.gateway.nacos.NacosRouteDefinitionParser;
import com.michael.chaos.gateway.nacos.NacosRouteDefinitionRepository;
import com.michael.chaos.gateway.nacos.NacosRouteDefinitionSynchronizer;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Gateway Nacos 动态路由自动装配测试。
 */
class ChaosGatewayNacosAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosGatewayNacosAutoConfiguration.class));

    /**
     * 开关未开启时不应注册动态路由组件。
     */
    @Test
    void shouldBackOffWhenDisabled() {
        contextRunner.withUserConfiguration(NacosConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(NacosRouteDefinitionRepository.class));
    }

    /**
     * 开关开启且存在 NacosConfigManager 时应注册动态路由组件。
     */
    @Test
    void shouldRegisterNacosRouteBeansWhenEnabled() {
        contextRunner.withUserConfiguration(NacosConfiguration.class)
                .withPropertyValues("chaos.gateway.nacos-routes.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChaosGatewayNacosRouteProperties.class);
                    assertThat(context).hasSingleBean(NacosRouteDefinitionParser.class);
                    assertThat(context).hasSingleBean(NacosRouteDefinitionRepository.class);
                    assertThat(context).hasSingleBean(RouteDefinitionRepository.class);
                    assertThat(context).hasSingleBean(NacosRouteDefinitionSynchronizer.class);
                });
    }

    /**
     * 业务已提供 RouteDefinitionRepository 时应退让。
     */
    @Test
    void shouldBackOffWhenRouteRepositoryProvided() {
        contextRunner.withUserConfiguration(NacosConfiguration.class, CustomRepositoryConfiguration.class)
                .withPropertyValues("chaos.gateway.nacos-routes.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(NacosRouteDefinitionRepository.class);
                    assertThat(context).doesNotHaveBean(NacosRouteDefinitionSynchronizer.class);
                    assertThat(context).hasSingleBean(RouteDefinitionRepository.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class NacosConfiguration {

        /**
         * 测试用 Nacos 配置管理器。
         */
        @Bean
        NacosConfigManager nacosConfigManager() {
            return new NacosConfigManager(new NacosConfigProperties());
        }

        /**
         * 测试用 Nacos 配置服务。
         */
        @Bean
        ConfigService configService() {
            return (ConfigService) Proxy.newProxyInstance(
                    ConfigService.class.getClassLoader(),
                    new Class<?>[]{ConfigService.class},
                    (proxy, method, args) -> {
                        if ("getConfig".equals(method.getName()) || "getConfigAndSignListener".equals(method.getName())
                                || "getServerStatus".equals(method.getName())) {
                            return "";
                        }
                        if (boolean.class.equals(method.getReturnType())) {
                            return false;
                        }
                        return null;
                    }
            );
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRepositoryConfiguration {

        /**
         * 自定义路由仓库。
         */
        @Bean
        RouteDefinitionRepository routeDefinitionRepository() {
            return new CustomRouteDefinitionRepository();
        }
    }

    static class CustomRouteDefinitionRepository implements RouteDefinitionRepository {

        @Override
        public Flux<org.springframework.cloud.gateway.route.RouteDefinition> getRouteDefinitions() {
            return Flux.empty();
        }

        @Override
        public Mono<Void> save(Mono<org.springframework.cloud.gateway.route.RouteDefinition> route) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> delete(Mono<String> routeId) {
            return Mono.empty();
        }
    }
}
