package com.michael.chaos.gateway.nacos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.RouteDefinition;

/**
 * Nacos 路由配置解析测试。
 */
class NacosRouteDefinitionParserTest {

    private final NacosRouteDefinitionParser parser = new NacosRouteDefinitionParser();

    /**
     * 应支持 Spring Cloud Gateway 原生 RouteDefinition YAML 格式。
     */
    @Test
    void shouldParseRouteDefinitionsFromYaml() {
        String content = """
                routes:
                  - id: order-service
                    uri: lb://order-service
                    order: 10
                    predicates:
                      - name: Path
                        args:
                          _genkey_0: /order/**
                    filters:
                      - name: StripPrefix
                        args:
                          _genkey_0: "1"
                    metadata:
                      owner: order
                """;

        List<RouteDefinition> routes = parser.parse(content);

        assertThat(routes).hasSize(1);
        RouteDefinition route = routes.getFirst();
        assertThat(route.getId()).isEqualTo("order-service");
        assertThat(route.getUri()).isEqualTo(URI.create("lb://order-service"));
        assertThat(route.getOrder()).isEqualTo(10);
        assertThat(route.getPredicates()).hasSize(1);
        assertThat(route.getPredicates().getFirst().getName()).isEqualTo("Path");
        assertThat(route.getFilters()).hasSize(1);
        assertThat(route.getFilters().getFirst().getName()).isEqualTo("StripPrefix");
        assertThat(route.getMetadata()).containsEntry("owner", "order");
    }

    /**
     * 应支持 Gateway 配置里常见的谓词和过滤器短写法。
     */
    @Test
    void shouldParseRouteDefinitionsWithShortcutDefinitions() {
        String content = """
                routes:
                  - id: order-service
                    uri: lb://order-service
                    predicates:
                      - Path=/order/**
                    filters:
                      - StripPrefix=1
                """;

        List<RouteDefinition> routes = parser.parse(content);

        assertThat(routes).hasSize(1);
        RouteDefinition route = routes.getFirst();
        assertThat(route.getPredicates().getFirst().getName()).isEqualTo("Path");
        assertThat(route.getPredicates().getFirst().getArgs()).containsEntry("_genkey_0", "/order/**");
        assertThat(route.getFilters().getFirst().getName()).isEqualTo("StripPrefix");
        assertThat(route.getFilters().getFirst().getArgs()).containsEntry("_genkey_0", "1");
    }

    /**
     * 应拒绝重复 route id，避免后写覆盖造成路由歧义。
     */
    @Test
    void shouldRejectDuplicateRouteIds() {
        String content = """
                routes:
                  - id: order-service
                    uri: lb://order-service
                    predicates:
                      - name: Path
                        args:
                          _genkey_0: /order/**
                  - id: order-service
                    uri: lb://order-service-v2
                    predicates:
                      - name: Path
                        args:
                          _genkey_0: /order-v2/**
                """;

        assertThatThrownBy(() -> parser.parse(content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate gateway route id");
    }

    /**
     * 应拒绝没有谓词的路由，避免无边界匹配。
     */
    @Test
    void shouldRejectRoutesWithoutPredicates() {
        String content = """
                routes:
                  - id: order-service
                    uri: lb://order-service
                """;

        assertThatThrownBy(() -> parser.parse(content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("predicates must not be empty");
    }
}
