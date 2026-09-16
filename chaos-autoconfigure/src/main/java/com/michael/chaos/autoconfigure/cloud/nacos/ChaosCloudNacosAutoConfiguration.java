package com.michael.chaos.autoconfigure.cloud.nacos;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Nacos 约定自动装配。
 *
 * <p>生效条件（同时满足）：</p>
 * <ul>
 *     <li>classpath 存在 nacos-client：此前没有任何 classpath 条件，未引入 Nacos 的网关、授权服务器也会注册约定 Bean，
 *     启动报告因此误报 cloud-nacos 已启用；</li>
 *     <li>Nacos discovery 与 config 没有被同时显式关闭：引入了 chaos-cloud-nacos-starter 但通过
 *     {@code spring.cloud.nacos.discovery.enabled=false} 和 {@code spring.cloud.nacos.config.enabled=false}
 *     关闭 Nacos（例如本地开发）时，命名约定没有意义；</li>
 *     <li>{@code chaos.nacos.enabled} 未关闭。</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.alibaba.nacos.api.NacosFactory")
@Conditional(ChaosCloudNacosAutoConfiguration.NacosInUseCondition.class)
@EnableConfigurationProperties(ChaosNacosProperties.class)
@ConditionalOnProperty(prefix = "chaos.nacos", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChaosCloudNacosAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NacosConventions nacosConventions(ChaosNacosProperties properties) {
        return new NacosConventions(properties);
    }

    /**
     * Nacos discovery 或 config 任一未被显式关闭即视为在用。
     */
    static class NacosInUseCondition extends AnyNestedCondition {

        NacosInUseCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "spring.cloud.nacos.discovery", name = "enabled", havingValue = "true",
                matchIfMissing = true)
        static class DiscoveryEnabled {
        }

        @ConditionalOnProperty(prefix = "spring.cloud.nacos.config", name = "enabled", havingValue = "true",
                matchIfMissing = true)
        static class ConfigEnabled {
        }
    }
}
