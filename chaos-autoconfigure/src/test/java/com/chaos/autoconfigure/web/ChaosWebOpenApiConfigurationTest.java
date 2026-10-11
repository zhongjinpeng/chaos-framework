package com.chaos.autoconfigure.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web OpenAPI OAuth2 自动装配测试。
 */
class ChaosWebOpenApiConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosWebAutoConfiguration.class));

    /**
     * 未显式开启时不应改变业务应用的 OpenAPI 契约。
     */
    @Test
    void shouldDisableOAuth2OpenApiByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ChaosOpenApiOAuth2Properties.class);
            assertThat(context).doesNotHaveBean("chaosOAuth2OpenApiCustomizer");
        });
    }

    /**
     * 多个密码登录入口应生成互为 OR 的独立安全方案。
     */
    @Test
    void shouldConfigureWebAndManagementPasswordFlows() {
        contextRunner.withPropertyValues(
                        "chaos.openapi.oauth2.enabled=true",
                        "chaos.openapi.oauth2.client-id=swagger-client",
                        "chaos.openapi.oauth2.client-secret=swagger-placeholder",
                        "chaos.openapi.oauth2.scopes[read]=读取数据",
                        "chaos.openapi.oauth2.scopes[write]=修改数据",
                        "chaos.openapi.oauth2.schemes[webOAuth2].token-url=/api/v1/auth/web/token",
                        "chaos.openapi.oauth2.schemes[webOAuth2].description=Web 用户登录",
                        "chaos.openapi.oauth2.schemes[managementOAuth2].token-url=/api/v1/auth/management/token",
                        "chaos.openapi.oauth2.schemes[managementOAuth2].description=管理端用户登录")
                .run(context -> {
                    OpenAPI openApi = new OpenAPI();
                    context.getBean("chaosOAuth2OpenApiCustomizer", OpenApiCustomizer.class).customise(openApi);

                    assertThat(openApi.getComponents().getSecuritySchemes())
                            .containsOnlyKeys("webOAuth2", "managementOAuth2");
                    assertPasswordFlow(openApi, "webOAuth2", "/api/v1/auth/web/token");
                    assertPasswordFlow(openApi, "managementOAuth2", "/api/v1/auth/management/token");
                    assertThat(openApi.getSecurity()).hasSize(2)
                            .anySatisfy(requirement -> assertThat(requirement)
                                    .containsEntry("webOAuth2", java.util.List.of("read", "write")))
                            .anySatisfy(requirement -> assertThat(requirement)
                                    .containsEntry("managementOAuth2", java.util.List.of("read", "write")));
                });
    }

    /**
     * 统一 token 基地址应只补全相对地址，不覆盖方案显式配置的绝对地址。
     */
    @Test
    void shouldResolveRelativeTokenUrlsAgainstConfiguredBase() {
        contextRunner.withPropertyValues(
                        "chaos.openapi.oauth2.enabled=true",
                        "chaos.openapi.oauth2.token-url-base=http://localhost:9090/gateway/",
                        "chaos.openapi.oauth2.scopes[read]=读取数据",
                        "chaos.openapi.oauth2.scopes[write]=修改数据",
                        "chaos.openapi.oauth2.schemes[webOAuth2].token-url=/api/v1/auth/web/token",
                        "chaos.openapi.oauth2.schemes[externalOAuth2].token-url=https://auth.example.com/oauth2/token")
                .run(context -> {
                    OpenAPI openApi = new OpenAPI();
                    context.getBean("chaosOAuth2OpenApiCustomizer", OpenApiCustomizer.class).customise(openApi);

                    assertPasswordFlow(
                            openApi, "webOAuth2", "http://localhost:9090/gateway/api/v1/auth/web/token");
                    assertPasswordFlow(openApi, "externalOAuth2", "https://auth.example.com/oauth2/token");
                });
    }

    /**
     * Swagger UI 应从框架配置预填客户端占位值，并自动选中所有已声明 scope。
     */
    @Test
    void shouldConfigureSwaggerUiOAuthDefaults() {
        contextRunner.withUserConfiguration(SpringdocOAuthTestConfiguration.class)
                .withPropertyValues(
                        "chaos.openapi.oauth2.enabled=true",
                        "chaos.openapi.oauth2.client-id=swagger-client",
                        "chaos.openapi.oauth2.client-secret=swagger-placeholder",
                        "chaos.openapi.oauth2.scopes[read]=读取数据",
                        "chaos.openapi.oauth2.scopes[write]=修改数据",
                        "chaos.openapi.oauth2.schemes[webOAuth2].token-url=/api/v1/auth/web/token",
                        "chaos.openapi.oauth2.schemes[managementOAuth2].token-url=/api/v1/auth/management/token")
                .run(context -> {
                    SwaggerUiOAuthProperties swaggerUiOAuthProperties =
                            context.getBean(SwaggerUiOAuthProperties.class);

                    assertThat(swaggerUiOAuthProperties.getClientId()).isEqualTo("swagger-client");
                    assertThat(swaggerUiOAuthProperties.getClientSecret()).isEqualTo("swagger-placeholder");
                    assertThat(swaggerUiOAuthProperties.getScopes()).containsExactly("read", "write");
                });
    }

    private static void assertPasswordFlow(OpenAPI openApi, String schemeName, String tokenUrl) {
        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get(schemeName);
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.OAUTH2);
        assertThat(scheme.getFlows().getPassword().getTokenUrl()).isEqualTo(tokenUrl);
        assertThat(scheme.getFlows().getPassword().getScopes()).containsOnlyKeys("read", "write");
    }

    @Configuration(proxyBeanMethods = false)
    static class SpringdocOAuthTestConfiguration {

        @Bean
        SwaggerUiOAuthProperties swaggerUiOAuthProperties() {
            return new SwaggerUiOAuthProperties();
        }
    }
}
