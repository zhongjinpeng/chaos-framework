package com.chaos.autoconfigure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaos.core.constant.ChaosHeaders;
import com.chaos.web.idempotent.DefaultIdempotentKeyGenerator;
import com.chaos.web.idempotent.Idempotent;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/**
 * Web OpenAPI 自动装配测试。
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

    /**
     * 默认幂等接口应在 OpenAPI 中声明必填请求头及与服务端一致的格式约束。
     */
    @Test
    void shouldDocumentRequiredIdempotencyKeyHeader() {
        contextRunner.run(context -> {
            OperationCustomizer customizer = context.getBean(
                    "chaosIdempotencyOperationCustomizer", OperationCustomizer.class);
            Operation operation = customizer.customize(new Operation(), handlerMethod("requiredKey"));

            assertThat(operation.getParameters()).singleElement().satisfies(parameter -> {
                assertThat(parameter.getName()).isEqualTo(ChaosHeaders.IDEMPOTENCY_KEY);
                assertThat(parameter.getIn()).isEqualTo("header");
                assertThat(parameter.getRequired()).isTrue();
                assertThat(parameter.getSchema().getMinLength()).isEqualTo(1);
                assertThat(parameter.getSchema().getMaxLength())
                        .isEqualTo(DefaultIdempotentKeyGenerator.MAX_REQUEST_KEY_LENGTH);
                assertThat(parameter.getSchema().getPattern()).isEqualTo("^[!-~]{1,128}$");
            });
        });
    }

    /**
     * 可选 key 应如实标记，固定 key、未标注方法和已手工声明请求头的方法不应重复生成参数。
     */
    @Test
    void shouldRespectOptionalFixedAndExplicitIdempotencyContracts() {
        contextRunner.run(context -> {
            OperationCustomizer customizer = context.getBean(
                    "chaosIdempotencyOperationCustomizer", OperationCustomizer.class);

            Operation optional = customizer.customize(new Operation(), handlerMethod("optionalKey"));
            assertThat(optional.getParameters()).singleElement()
                    .satisfies(parameter -> assertThat(parameter.getRequired()).isFalse());

            Operation fixed = customizer.customize(new Operation(), handlerMethod("fixedKey"));
            Operation plain = customizer.customize(new Operation(), handlerMethod("plain"));
            assertThat(fixed.getParameters()).isNull();
            assertThat(plain.getParameters()).isNull();

            Parameter documented = new Parameter()
                    .name(ChaosHeaders.IDEMPOTENCY_KEY)
                    .in("header")
                    .required(true);
            Operation explicit = new Operation().addParametersItem(documented);
            customizer.customize(explicit, handlerMethod("requiredKey"));
            assertThat(explicit.getParameters()).containsExactly(documented);
        });
    }

    private static void assertPasswordFlow(OpenAPI openApi, String schemeName, String tokenUrl) {
        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get(schemeName);
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.OAUTH2);
        assertThat(scheme.getFlows().getPassword().getTokenUrl()).isEqualTo(tokenUrl);
        assertThat(scheme.getFlows().getPassword().getScopes()).containsOnlyKeys("read", "write");
    }

    private static HandlerMethod handlerMethod(String methodName) {
        try {
            return new HandlerMethod(
                    new IdempotencyEndpoints(), IdempotencyEndpoints.class.getDeclaredMethod(methodName));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("找不到幂等测试接口方法: " + methodName, ex);
        }
    }

    static class IdempotencyEndpoints {

        @Idempotent
        void requiredKey() {
        }

        @Idempotent(requireKey = false)
        void optionalKey() {
        }

        @Idempotent(key = "server-controlled")
        void fixedKey() {
        }

        void plain() {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SpringdocOAuthTestConfiguration {

        @Bean
        SwaggerUiOAuthProperties swaggerUiOAuthProperties() {
            return new SwaggerUiOAuthProperties();
        }
    }
}
