package com.michael.chaos.autoconfigure.cloud.nacos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Nacos 约定自动装配测试。
 */
class ChaosCloudNacosAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosCloudNacosAutoConfiguration.class));

    /**
     * 默认约定应生成 dataId 和元数据键。
     */
    @Test
    void shouldRegisterDefaultConventions() {
        contextRunner.run(context -> {
            NacosConventions conventions = context.getBean(NacosConventions.class);
            assertThat(conventions.dataId("order-service", null)).isEqualTo("chaos-order-service-default.yaml");
            assertThat(conventions.grayMetadataKey()).isEqualTo("chaos.gray");
            assertThat(conventions.tenantMetadataKey()).isEqualTo("chaos.tenant");
        });
    }

    /**
     * 自定义前缀应生效。
     */
    @Test
    void shouldBindCustomPrefixes() {
        contextRunner.withPropertyValues("chaos.nacos.data-id-prefix=biz", "chaos.nacos.metadata-prefix=acme")
                .run(context -> {
                    NacosConventions conventions = context.getBean(NacosConventions.class);
                    assertThat(conventions.dataId("order-service", " prod ")).isEqualTo("biz-order-service-prod.yaml");
                    assertThat(conventions.grayMetadataKey()).isEqualTo("acme.gray");
                });
    }

    /**
     * 关闭开关后不注册约定 Bean。
     */
    @Test
    void shouldBackOffWhenDisabled() {
        contextRunner.withPropertyValues("chaos.nacos.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(NacosConventions.class));
    }

    /**
     * 未引入 nacos-client 的应用（网关、授权服务器）不应注册约定 Bean，启动报告也不应显示 cloud-nacos 已启用。
     */
    @Test
    void shouldBackOffWithoutNacosClient() {
        contextRunner.withClassLoader(new FilteredClassLoader("com.alibaba.nacos"))
                .run(context -> assertThat(context).doesNotHaveBean(NacosConventions.class));
    }

    /**
     * discovery 与 config 同时显式关闭时视为未使用 Nacos。
     */
    @Test
    void shouldBackOffWhenDiscoveryAndConfigBothDisabled() {
        contextRunner.withPropertyValues(
                        "spring.cloud.nacos.discovery.enabled=false",
                        "spring.cloud.nacos.config.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(NacosConventions.class));
    }

    /**
     * 只关闭其中一项时仍在使用 Nacos，约定继续生效。
     */
    @Test
    void shouldStayActiveWhenOnlyConfigDisabled() {
        contextRunner.withPropertyValues("spring.cloud.nacos.config.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(NacosConventions.class));
    }
}
