package com.michael.chaos.autoconfigure.gateway.nacos;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.michael.chaos.gateway.nacos.ChaosGatewayNacosRouteProperties;
import com.michael.chaos.gateway.nacos.NacosRouteDefinitionParser;
import com.michael.chaos.gateway.nacos.NacosRouteDefinitionRepository;
import com.michael.chaos.gateway.nacos.NacosRouteDefinitionSynchronizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * Gateway Nacos 动态路由自动装配。
 */
@AutoConfiguration(
        beforeName = "org.springframework.cloud.gateway.config.GatewayAutoConfiguration",
        afterName = "com.alibaba.cloud.nacos.NacosConfigAutoConfiguration"
)
@ConditionalOnClass(value = {RouteDefinitionRepository.class, NacosConfigManager.class, ConfigService.class}, name = "com.michael.chaos.gateway.nacos.ChaosGatewayNacosRouteProperties")
@ConditionalOnBean(NacosConfigManager.class)
@ConditionalOnProperty(prefix = "chaos.gateway.nacos-routes", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ChaosGatewayNacosRouteProperties.class)

public class ChaosGatewayNacosAutoConfiguration {

    /**
     * 注册 Nacos 路由配置解析器。
     */
    @Bean
    @ConditionalOnMissingBean
    public NacosRouteDefinitionParser nacosRouteDefinitionParser() {
        return new NacosRouteDefinitionParser();
    }

    /**
     * 注册 Nacos 路由仓库。
     */
    @Bean
    @ConditionalOnMissingBean(RouteDefinitionRepository.class)
    public NacosRouteDefinitionRepository nacosRouteDefinitionRepository() {
        return new NacosRouteDefinitionRepository();
    }

    /**
     * 注册 Nacos 路由同步器。
     */
    @Bean
    @ConditionalOnBean(NacosRouteDefinitionRepository.class)
    @ConditionalOnMissingBean
    public NacosRouteDefinitionSynchronizer nacosRouteDefinitionSynchronizer(
            NacosConfigManager nacosConfigManager,
            ChaosGatewayNacosRouteProperties properties,
            NacosRouteDefinitionParser parser,
            NacosRouteDefinitionRepository repository,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<ConfigService> configServiceProvider) {
        ConfigService configService = configServiceProvider.getIfAvailable(nacosConfigManager::getConfigService);
        return new NacosRouteDefinitionSynchronizer(configService, properties, parser, repository, eventPublisher);
    }
}
