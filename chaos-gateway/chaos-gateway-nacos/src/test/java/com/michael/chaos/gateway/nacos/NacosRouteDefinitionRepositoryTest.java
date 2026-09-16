package com.michael.chaos.gateway.nacos;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import reactor.test.StepVerifier;

/**
 * Nacos 路由仓库测试。
 */
class NacosRouteDefinitionRepositoryTest {

    /**
     * 应原子替换当前路由表。
     */
    @Test
    void shouldReplaceRouteDefinitions() {
        NacosRouteDefinitionRepository repository = new NacosRouteDefinitionRepository();
        repository.replaceRoutes(List.of(route("order-service", "lb://order-service")));

        StepVerifier.create(repository.getRouteDefinitions())
                .assertNext(route -> assertThat(route.getId()).isEqualTo("order-service"))
                .verifyComplete();

        repository.replaceRoutes(List.of(route("product-service", "lb://product-service")));

        StepVerifier.create(repository.getRouteDefinitions())
                .assertNext(route -> assertThat(route.getId()).isEqualTo("product-service"))
                .verifyComplete();
    }

    /**
     * 写入操作应被拒绝，Nacos 是唯一写入源。
     */
    @Test
    void shouldRejectManualWrites() {
        NacosRouteDefinitionRepository repository = new NacosRouteDefinitionRepository();

        StepVerifier.create(repository.save(null))
                .expectError(UnsupportedOperationException.class)
                .verify();
        StepVerifier.create(repository.delete(null))
                .expectError(UnsupportedOperationException.class)
                .verify();
    }

    private RouteDefinition route(String id, String uri) {
        RouteDefinition route = new RouteDefinition();
        route.setId(id);
        route.setUri(URI.create(uri));
        route.setPredicates(List.of(new PredicateDefinition("Path=/" + id + "/**")));
        return route;
    }
}
